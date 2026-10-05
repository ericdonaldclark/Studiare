package net.ericclark.studiare.components.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.ericclark.studiare.SherpaModelRepo
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/** Why [SpeechEngine.speak] didn't produce audio. */
enum class SpeechFailureReason { NOT_READY, REJECTED, TIMED_OUT }

sealed class SpeechResult {
    data object Success : SpeechResult()
    data class Failed(val reason: SpeechFailureReason) : SpeechResult()
}

/**
 * Text-to-speech for the study modes: Sherpa-ONNX's offline "HD" voices first, the Android
 * system [TextToSpeech] as a fallback. One instance is meant to be held for as long as a
 * screen/service needs TTS and [release]d when it's done.
 *
 * Extracted from `AudioStudyService` (previously `speakText`/`getSherpaTtsForLang`/
 * `playSherpaAudio`, private to that service) so the new Speech-to-Text, Text-to-Speech,
 * Listen & Speak and Listen & Type modes can reuse the exact same, already-hardened playback
 * path instead of a second copy — including the timeout below, which is not optional
 * polish: a stuck system voice (e.g. a network voice with no route out — confirmed on the
 * dev emulator with an Italian voice that reports `isSpeaking() == true` forever) used to
 * hang the entire study loop indefinitely with no error and no audio. See the Studiare
 * roadmap plan's "Phase 0" entry for how this was diagnosed.
 */
class SpeechEngine(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val filesDir: File get() = appContext.filesDir

    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var ttsReady = false
    // Constructing TextToSpeech is async — onInit fires on a callback some time later (typically
    // tens to hundreds of ms, but not instant). Each mode screen builds its own SpeechEngine, and
    // that screen's very first speak() call (its front-card autoplay) can easily race the engine
    // still binding — confirmed as the cause of Listen & Type's front audio silently not playing
    // on session start. speak() awaits this instead of failing immediately when !ttsReady.
    private val readyDeferred = CompletableDeferred<Boolean>()

    private var sherpaTts: OfflineTts? = null
    private var currentSherpaLang: String? = null
    private var audioTrack: AudioTrack? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            // No-op listener: completion is awaited by polling isSpeaking() with a timeout
            // in speak() below, not via this callback. See speak()'s comment for why.
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
            })
            readyDeferred.complete(true)
        } else {
            readyDeferred.complete(false)
        }
    }

    /**
     * Speaks `text` (plus `notes`, if any) in `languageCode`. Suspends until the audio has
     * actually finished (or a bounded timeout elapses), not just until the engine accepted
     * the request.
     *
     * @param shouldContinue polled while waiting for system-TTS playback to finish; returning
     *   false ends the wait early (e.g. the caller was paused/stopped) without treating it as
     *   a failure. Sherpa playback is a single blocking write and isn't interruptible this way.
     * @param speechRate 1.0 is normal speed; above 1.0 is faster, below is slower. Applies to both
     *   Sherpa HD and system TTS.
     */
    suspend fun speak(
        text: String,
        notes: String? = null,
        languageCode: String,
        shouldContinue: () -> Boolean = { true },
        speechRate: Float = 1f
    ): SpeechResult {
        val rawText = if (notes.isNullOrBlank()) text else "$text. $notes"
        val sanitizedText = rawText.lowercase()

        // 1. Try Sherpa HD TTS
        val sherpaAudio = withContext(Dispatchers.IO) {
            val ttsEngine = getSherpaTtsForLang(languageCode)
            if (ttsEngine != null) {
                Log.i(TAG, "Executing Sherpa-Onnx HD TTS for [$languageCode]")
                ttsEngine.generate(sanitizedText, 0, speechRate)
            } else {
                null
            }
        }

        if (sherpaAudio != null) {
            playSherpaAudio(sherpaAudio.samples, sherpaAudio.sampleRate)
            return SpeechResult.Success
        }

        // 2. Fallback to System TTS
        Log.i(TAG, "Falling back to Native Android TTS for [$languageCode]")

        val engine = tts
        if (engine == null) {
            Log.e(TAG, "Native TTS engine is not ready or null.")
            return SpeechResult.Failed(SpeechFailureReason.NOT_READY)
        }
        if (!ttsReady) {
            val bound = withTimeoutOrNull(3000) { readyDeferred.await() } ?: false
            if (!bound) {
                Log.e(TAG, "Native TTS engine never finished binding (timed out waiting for onInit).")
                return SpeechResult.Failed(SpeechFailureReason.NOT_READY)
            }
        }

        val locale = Locale(languageCode)
        val result = engine.isLanguageAvailable(locale)
        if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            engine.language = locale
        } else {
            Log.w(TAG, "Language $languageCode not supported by Native TTS, defaulting to English.")
            engine.language = Locale.ENGLISH
        }

        // Prefer a voice that runs on-device over one that needs a network round-trip.
        // Some engines default `tts.language = locale` to a server-side voice; when that
        // server call stalls (blocked/slow network, no account, etc.) `isSpeaking()` reports
        // true indefinitely with nothing ever audible or erroring — see the timeout below,
        // which is the actual safety net, but avoiding the network voice avoids the stall
        // in the first place whenever an offline alternative exists.
        val offlineVoice = engine.voices
            ?.filter { it.locale.language == engine.language.language && !it.isNetworkConnectionRequired }
            ?.maxByOrNull { it.quality }
        if (offlineVoice != null && offlineVoice != engine.voice) {
            engine.voice = offlineVoice
        }

        val currentVoice = engine.voice
        if (currentVoice != null) {
            Log.i(TAG, "Native Model -> Name: ${currentVoice.name}, Locale: ${currentVoice.locale}, network: ${currentVoice.isNetworkConnectionRequired}")
        } else {
            Log.i(TAG, "Native Model -> Default (No specific voice info available)")
        }

        engine.setSpeechRate(speechRate)
        val uuid = UUID.randomUUID().toString()
        val queued = engine.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, null, uuid)
        if (queued != TextToSpeech.SUCCESS) {
            Log.e(TAG, "speak() rejected the request (code $queued) for [$languageCode]")
            return SpeechResult.Failed(SpeechFailureReason.REJECTED)
        }

        // Bound the whole wait on a timeout. Confirmed on-device: a stuck network voice can
        // leave isSpeaking() == true forever — no crash, no onError, no audio — which used to
        // hang the entire study loop indefinitely (reproduced: 100+s with no progress). The
        // timeout scales with text length so real speech has room to finish, with a floor for
        // very short words and a ceiling so a genuine stall still recovers quickly.
        val timeoutMs = (sanitizedText.split(" ").size * 500L + 2000L).coerceIn(4000L, 15000L)
        val completed = withTimeoutOrNull(timeoutMs) {
            // Confirm the engine actually started producing audio before treating this as a
            // success. Without this check, a misconfigured/voiceless engine (no crash, no
            // onError — just silence) makes speak() look like it worked: isSpeaking() is
            // false from the very first check below, so it would fall straight through as if
            // the word had been spoken — indistinguishable from "nothing happens."
            while (engine.isSpeaking != true) {
                delay(50)
            }
            while (engine.isSpeaking == true && shouldContinue()) {
                delay(100)
            }
            true
        }

        if (completed == null) {
            Log.e(TAG, "Timed out waiting for speech for [$languageCode] — engine may be stuck (e.g. a stalled network voice) or no voice is configured")
            engine.stop()
            return SpeechResult.Failed(SpeechFailureReason.TIMED_OUT)
        }

        if (!shouldContinue()) {
            engine.stop()
        }
        return SpeechResult.Success
    }

    /** Stops whichever engine is currently producing sound. Safe to call when idle. */
    fun stop() {
        tts?.stop()
        try {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.stop()
            }
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        }
    }

    /** Releases both engines. The instance must not be used again after this. */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        sherpaTts?.release()
        sherpaTts = null
        audioTrack?.release()
        audioTrack = null
    }

    private fun getSherpaTtsForLang(langCode: String): OfflineTts? {
        if (sherpaTts != null && currentSherpaLang == langCode) {
            return sherpaTts
        }

        val modelConfig = SherpaModelRepo.getModelForLanguage(langCode, "TTS") ?: return null
        val modelDir = File(filesDir, "sherpa_models/${modelConfig.modelName}")

        if (!modelDir.exists()) return null

        // 1. Find Files (as File objects)
        val onnxFile = modelDir.listFiles()?.find { it.name.endsWith(".onnx") }
        val tokensFile = modelDir.listFiles()?.find { it.name == "tokens.txt" }
        val dataDir = modelDir.listFiles()?.find { it.isDirectory && it.name.startsWith("espeak-ng-data") }

        // 2. Safety Check: Ensure files exist and are not empty (0 bytes = corrupt)
        if (onnxFile == null || onnxFile.length() == 0L ||
            tokensFile == null || tokensFile.length() == 0L) {
            Log.e(TAG, "Model files found but appear corrupt/empty. Falling back to System TTS.")
            return null
        }

        try {
            // 3. Configure
            val vitsConfig = OfflineTtsVitsModelConfig(
                model = onnxFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = dataDir?.absolutePath ?: "",
                noiseScale = 0.5f,
                noiseScaleW = 0.8f,
                lengthScale = 1.15f
            )

            val modelConf = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 1,
                debug = true,
                provider = "cpu"
            )
            val config = OfflineTtsConfig(model = modelConf)

            sherpaTts?.release()

            // FIX: Set assetManager to null because we are using absolute file paths
            sherpaTts = OfflineTts(assetManager = null, config = config)
            currentSherpaLang = langCode
            Log.d(TAG, "Sherpa TTS initialized for $langCode")
            return sherpaTts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Sherpa TTS", e)
            return null
        }
    }

    private suspend fun playSherpaAudio(samples: FloatArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        if (audioTrack == null || audioTrack?.sampleRate != sampleRate) {
            audioTrack?.release()

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(max(bufferSize, samples.size * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }

        // Pad with a little silence so the very last syllable isn't cut off by stop()/flush().
        val paddingSamples = (sampleRate * 0.5).toInt()
        val paddedSamples = FloatArray(samples.size + paddingSamples)
        System.arraycopy(samples, 0, paddedSamples, 0, samples.size)

        audioTrack?.play()
        audioTrack?.write(paddedSamples, 0, paddedSamples.size, AudioTrack.WRITE_BLOCKING)

        delay(100)

        audioTrack?.stop()
        audioTrack?.flush()
    }

    companion object {
        private const val TAG = "SpeechEngine"
    }
}
