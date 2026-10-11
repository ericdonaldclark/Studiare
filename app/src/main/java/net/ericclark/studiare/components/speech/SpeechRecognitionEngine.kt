package net.ericclark.studiare.components.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class RecognitionFailureReason {
    NO_PERMISSION,
    /** No Whisper model is downloaded and the system recognizer isn't available either. */
    NO_MODEL,
    NO_SPEECH_DETECTED,
    RECOGNIZER_UNAVAILABLE
}

sealed class RecognitionResult {
    data class Recognized(val text: String) : RecognitionResult()
    data class Failed(val reason: RecognitionFailureReason) : RecognitionResult()
}

/**
 * Speech-to-text for the spoken-answer study modes (Speech-to-Text, Listen & Speak). Tries an
 * on-device Whisper model first — downloaded via Settings -> Speech Recognition, see
 * [WhisperModelRepo] — segmenting the microphone stream with Silero VAD so it decodes one
 * complete utterance instead of a fixed-length clip. Falls back to Android's system
 * [SpeechRecognizer] when no Whisper model is installed, or when Whisper hears nothing.
 *
 * Checking [android.Manifest.permission.RECORD_AUDIO] is this class's job (a missing
 * permission is just another [RecognitionResult.Failed] reason); actually *requesting* it from
 * the user is the caller's, the same way `AudioMode.kt` already does for the legacy Audio mode.
 *
 * One instance should be held for as long as a screen needs recognition and [release]d when
 * done — loading a Whisper model is too expensive to redo on every call.
 */
class SpeechRecognitionEngine(context: Context) {
    private val appContext = context.applicationContext

    private var whisperRecognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var loadedSize: WhisperModelSize? = null
    private var loadedLanguage: String? = null

    private var systemRecognizer: SpeechRecognizer? = null

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** True once a Whisper model of this size, plus the VAD model, are both on disk. */
    fun isWhisperAvailable(size: WhisperModelSize?): Boolean {
        if (size == null) return false
        return WhisperModelRepo.installedFiles(appContext, size) != null &&
            WhisperModelRepo.vadModelFile(appContext).exists()
    }

    /**
     * Listens on the microphone for up to [maxListenMs] and returns what was heard. One call is
     * one attempt — the caller (a mode's retry loop) decides whether and how many times to try
     * again, matching the pattern the legacy Audio mode already uses for its 3-attempt retry.
     */
    suspend fun listen(
        languageCode: String,
        whisperSize: WhisperModelSize?,
        maxListenMs: Long = 8000
    ): RecognitionResult {
        if (!hasRecordAudioPermission()) {
            return RecognitionResult.Failed(RecognitionFailureReason.NO_PERMISSION)
        }

        val whisperLanguage = mapToWhisperLanguage(languageCode)
        if (whisperSize != null && loadWhisperIfNeeded(whisperSize, whisperLanguage)) {
            val text = listenWithWhisper(maxListenMs)
            if (!text.isNullOrBlank()) return RecognitionResult.Recognized(text)
            // Whisper ran but produced nothing (silence, or a misfire) — try the system
            // recognizer before giving up, per the roadmap plan's fallback order.
        }

        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            val text = listenWithSystemRecognizer(languageCode)
            return if (!text.isNullOrBlank()) {
                RecognitionResult.Recognized(text)
            } else {
                RecognitionResult.Failed(RecognitionFailureReason.NO_SPEECH_DETECTED)
            }
        }

        return RecognitionResult.Failed(
            if (whisperSize == null) RecognitionFailureReason.NO_MODEL else RecognitionFailureReason.RECOGNIZER_UNAVAILABLE
        )
    }

    private fun loadWhisperIfNeeded(size: WhisperModelSize, whisperLanguage: String?): Boolean {
        if (whisperRecognizer != null && vad != null && loadedSize == size && loadedLanguage == whisperLanguage) return true

        val files = WhisperModelRepo.installedFiles(appContext, size) ?: return false
        val vadFile = WhisperModelRepo.vadModelFile(appContext)
        if (!vadFile.exists()) return false

        return try {
            whisperRecognizer?.release()
            vad?.release()

            val whisperConfig = OfflineWhisperModelConfig(
                encoder = files.encoder.absolutePath,
                decoder = files.decoder.absolutePath,
                // Forced when the deck's language code maps cleanly onto one of Whisper's ~99
                // languages (see mapToWhisperLanguage) — measurably more accurate than guessing,
                // especially on short single words. Falls back to auto-detect ("") otherwise,
                // since a deck's code doesn't always match Whisper's (region-qualified codes like
                // "pt-BR", legacy codes like "iw"/"in", or a language Whisper doesn't recognize).
                language = whisperLanguage ?: "",
                task = "transcribe",
                tailPaddings = -1,
                enableTokenTimestamps = false,
                enableSegmentTimestamps = false
            )
            val modelConfig = OfflineModelConfig(
                whisper = whisperConfig,
                tokens = files.tokens.absolutePath,
                numThreads = 1,
                debug = false,
                provider = "cpu",
                modelType = "whisper"
            )
            val recognizerConfig = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )
            whisperRecognizer = OfflineRecognizer(assetManager = null, config = recognizerConfig)

            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadFile.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f, // half a second of silence = utterance is over
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = 8f
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu"
            )
            vad = Vad(assetManager = null, config = vadConfig)

            loadedSize = size
            loadedLanguage = whisperLanguage
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Whisper model ($size)", e)
            whisperRecognizer?.release()
            vad?.release()
            whisperRecognizer = null
            vad = null
            loadedSize = null
            loadedLanguage = null
            false
        }
    }

    private suspend fun listenWithWhisper(maxListenMs: Long): String? = withContext(Dispatchers.IO) {
        val recognizer = whisperRecognizer ?: return@withContext null
        val vadInstance = vad ?: return@withContext null

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 2
        val audioRecord = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufferSize)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open AudioRecord", e)
            return@withContext null
        }

        val buffer = FloatArray(bufferSize / 4)
        var result: String? = null
        val deadline = System.currentTimeMillis() + maxListenMs

        fun decodePendingSegments() {
            while (!vadInstance.empty()) {
                val segment = vadInstance.front()
                val stream = recognizer.createStream()
                stream.acceptWaveform(segment.samples, SAMPLE_RATE)
                recognizer.decode(stream)
                val text = recognizer.getResult(stream).text
                stream.release()
                vadInstance.pop()
                if (result == null && text.isNotBlank()) {
                    result = text
                }
            }
        }

        try {
            vadInstance.reset()
            audioRecord.startRecording()
            while (result == null && System.currentTimeMillis() < deadline) {
                val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    vadInstance.acceptWaveform(if (read == buffer.size) buffer else buffer.copyOfRange(0, read))
                    decodePendingSegments()
                }
            }
            if (result == null) {
                // The deadline hit mid-utterance (e.g. a slow talker) — flush whatever VAD is
                // still holding instead of throwing away a real answer.
                vadInstance.flush()
                decodePendingSegments()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Whisper listen failed", e)
        } finally {
            try {
                audioRecord.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
            audioRecord.release()
        }
        result
    }

    private suspend fun listenWithSystemRecognizer(languageCode: String): String? {
        val mainHandler = Handler(appContext.mainLooper)
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                mainHandler.post {
                    val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                    systemRecognizer = recognizer

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    }

                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            if (cont.isActive) cont.resume(null)
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (cont.isActive) cont.resume(matches?.firstOrNull())
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    recognizer.startListening(intent)
                }

                cont.invokeOnCancellation {
                    mainHandler.post {
                        systemRecognizer?.stopListening()
                        systemRecognizer?.destroy()
                        systemRecognizer = null
                    }
                }
            }
        }.also {
            mainHandler.post {
                systemRecognizer?.destroy()
                systemRecognizer = null
            }
        }
    }

    fun release() {
        whisperRecognizer?.release()
        whisperRecognizer = null
        vad?.release()
        vad = null
        loadedSize = null
        systemRecognizer?.destroy()
        systemRecognizer = null
    }

    companion object {
        private const val TAG = "SpeechRecognitionEngine"
        private const val SAMPLE_RATE = 16000

        /**
         * Whisper's ~99 supported languages (from OpenAI's `tokenizer.py` `LANGUAGES` table) —
         * used to decide whether a deck's language code can be forced rather than auto-detected.
         * A handful of legacy/alternate codes a deck might use are mapped onto Whisper's spelling
         * first (Java's old `iw`/`in`/`ji`, and `jv` for Javanese, which Whisper spells `jw`).
         */
        private val WHISPER_LANGUAGES = setOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar",
            "sv", "it", "id", "hi", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu",
            "ta", "no", "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk", "te", "fa",
            "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy", "ne", "mn",
            "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc",
            "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn",
            "mt", "sa", "lb", "my", "bo", "tl", "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw",
            "su", "yue"
        )
        private val LEGACY_CODE_ALIASES = mapOf(
            "iw" to "he", // old Java/Android code for Hebrew
            "in" to "id", // old Java/Android code for Indonesian
            "ji" to "yi", // old code for Yiddish
            "jv" to "jw"  // ISO 639-1 Javanese vs. Whisper's spelling
        )

        /**
         * Maps a deck's language code (may be a bare ISO-639-1 code, or region-qualified like
         * `pt-BR`/`zh_CN`) onto a Whisper language code, or null if there's no clean mapping (in
         * which case the caller should let Whisper auto-detect instead of forcing a bad guess).
         */
        fun mapToWhisperLanguage(deckLanguageCode: String): String? {
            val primary = deckLanguageCode.substringBefore('-').substringBefore('_').lowercase()
            val resolved = LEGACY_CODE_ALIASES[primary] ?: primary
            return resolved.takeIf { it in WHISPER_LANGUAGES }
        }
    }
}
