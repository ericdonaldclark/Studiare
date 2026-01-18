package net.ericclark.studiare

import android.content.pm.PackageManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.withContext
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import androidx.core.app.ActivityCompat
import net.ericclark.studiare.data.*
// Sherpa Imports

import kotlinx.coroutines.*
import kotlin.coroutines.resume
// Math Imports
import kotlin.math.max

class AudioStudyService : android.app.Service(), TextToSpeech.OnInitListener {

    private var lastRewindTime: Long = 0
    private val REWIND_THRESHOLD_MS = 2000L
    private val binder = LocalBinder()

    // System TTS
    private var tts: TextToSpeech? = null

    // Sherpa TTS
    private var sherpaTts: OfflineTts? = null
    private var currentSherpaLang: String? = null
    private var audioTrack: AudioTrack? = null

    private var sherpaStt: OnlineRecognizer? = null
    private var currentSherpaSttLang: String? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaSession: MediaSession? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var studyJob: Job? = null
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    // State exposed to ViewModel/UI
    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage

    // Configuration
    var cards: List<net.ericclark.studiare.data.Card> = emptyList()
    var frontLanguageStr: String = Locale.getDefault().language
    var backLanguageStr: String = Locale.getDefault().language
    var enableStt: Boolean = false
    var isGraded: Boolean = false

    var promptSide: String = "Front"
    var hideAnswerText: Boolean = false

    // Delays in Milliseconds
    var answerDelayMs: Long = 2000L
    var nextCardDelayMs: Long = 2000L

    // Continuous Play Toggle
    var continuousPlay: Boolean = true

    private var ttsReady = false
    private val CHANNEL_ID = "AudioStudyChannel"

    private var currentCardSolved: Boolean = false

    private val _cardResults = MutableSharedFlow<Pair<String, Boolean>>()
    val cardResults: SharedFlow<Pair<String, Boolean>> = _cardResults

    inner class LocalBinder : android.os.Binder() {
        fun getService(): AudioStudyService = this@AudioStudyService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupMediaSession()
        createNotificationChannel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
            })
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "AudioStudySession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { resumeStudy() }


                override fun onPause() { pauseStudy() }
                override fun onStop() { stopSelf() }
                override fun onSkipToNext() { skipToNext() }
                override fun onSkipToPrevious() { skipToPrevious() }
            })
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    // --- Sherpa TTS Setup ---
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
            Log.e("AudioStudyService", "Model files found but appear corrupt/empty. Falling back to System TTS.")
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

            // 4. Initialize
            // FIX: Set assetManager to null because we are using absolute file paths
            sherpaTts = OfflineTts(assetManager = null, config = config)
            currentSherpaLang = langCode
            Log.d("AudioStudyService", "Sherpa TTS initialized for $langCode")
            return sherpaTts
        } catch (e: Exception) {
            Log.e("AudioStudyService", "Failed to init Sherpa TTS", e)
            return null
        }
    }

    private fun getSherpaSttForLang(langCode: String): OnlineRecognizer? {
        if (sherpaStt != null && currentSherpaSttLang == langCode) {
            return sherpaStt
        }

        val modelConfig = SherpaModelRepo.getModelForLanguage(langCode, "STT") ?: return null
        val modelDir = File(filesDir, "sherpa_models/${modelConfig.modelName}")

        if (!modelDir.exists()) return null

        val encoderFile = modelDir.listFiles()?.find { it.name.contains("encoder") && it.name.endsWith(".onnx") }?.absolutePath ?: return null
        val decoderFile = modelDir.listFiles()?.find { it.name.contains("decoder") && it.name.endsWith(".onnx") }?.absolutePath ?: return null
        val joinerFile = modelDir.listFiles()?.find { it.name.contains("joiner") && it.name.endsWith(".onnx") }?.absolutePath ?: return null
        val tokensFile = modelDir.listFiles()?.find { it.name == "tokens.txt" }?.absolutePath ?: return null

        try {
            // 1. Configure Transducer (Model files only)
            val transducerConfig = OnlineTransducerModelConfig(
                encoder = encoderFile,
                decoder = decoderFile,
                joiner = joinerFile
            )

            // 2. Configure General Model Settings
            val modelConf = OnlineModelConfig(
                transducer = transducerConfig,
                tokens = tokensFile,
                numThreads = 1,
                debug = false,
                provider = "cpu",
                modelType = "zipformer"
            )

            // 3. Configure Endpointing (Silence detection)
            // Rule 1: Stop if silence > 2.4s
            val rule1 = EndpointRule(
                mustContainNonSilence = false,
                minTrailingSilence = 2.4f,
                minUtteranceLength = 0.0f
            )
            // Rule 2: Stop if silence > 1.2s AND we have detected speech
            val rule2 = EndpointRule(
                mustContainNonSilence = true,
                minTrailingSilence = 1.2f,
                minUtteranceLength = 0.0f
            )
            // Rule 3: Stop if utterance > 20s
            val rule3 = EndpointRule(
                mustContainNonSilence = false,
                minTrailingSilence = 0.0f,
                minUtteranceLength = 20.0f
            )

            val endpointConfig = EndpointConfig(
                rule1 = rule1,
                rule2 = rule2,
                rule3 = rule3
            )

            val config = OnlineRecognizerConfig(
                modelConfig = modelConf,
                endpointConfig = endpointConfig,
                enableEndpoint = true,
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )

            sherpaStt?.release()
            // FIX: Set assetManager to null because we are using absolute file paths
            sherpaStt = OnlineRecognizer(assetManager = null, config = config)
            currentSherpaSttLang = langCode
            Log.d("AudioStudyService", "Sherpa STT initialized for $langCode")
            return sherpaStt

        } catch (e: Exception) {
            Log.e("AudioStudyService", "Failed to init Sherpa STT", e)
            return null
        }
    }

    private suspend fun runSherpaStt(recognizer: OnlineRecognizer): String? = withContext(Dispatchers.IO) {
        if (ActivityCompat.checkSelfPermission(this@AudioStudyService, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AudioStudyService", "Microphone permission not granted for Service")
            return@withContext null
        }

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 2
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufferSize)
        val buffer = FloatArray(bufferSize / 4)

        val stream = recognizer.createStream()

        _isListening.value = true
        audioRecord.startRecording()

        var result: String? = null
        try {
            while (_isPlaying.value && _isListening.value) {
                val ret = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (ret > 0) {
                    stream.acceptWaveform(buffer.sliceArray(0 until ret), sampleRate)

                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }

                    // Check for endpoint (silence detection)
                    if (recognizer.isEndpoint(stream)) {
                        val text = recognizer.getResult(stream).text
                        if (text.isNotBlank()) {
                            result = text
                            _isListening.value = false // Break loop
                        }
                        recognizer.reset(stream)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioStudyService", "Sherpa STT Error", e)
        } finally {
            try {
                audioRecord.stop()
                audioRecord.release()
            } catch (e: Exception) { Log.e("AudioStudy", "Error releasing AudioRecord", e) }
            stream.release()
            _isListening.value = false
        }
        return@withContext result
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
                .setTransferMode(AudioTrack.MODE_STREAM) // Fixed: Changed from MODE_WRITE_ONLY to MODE_STREAM
                .build()
        }

        // 1. Create a new array with padding (e.g., 0.5 seconds of silence)
        val paddingSamples = (sampleRate * 0.5).toInt()
        val paddedSamples = FloatArray(samples.size + paddingSamples)

        // Copy original speech
        System.arraycopy(samples, 0, paddedSamples, 0, samples.size)

        audioTrack?.play()
        audioTrack?.write(paddedSamples, 0, paddedSamples.size, AudioTrack.WRITE_BLOCKING)

        delay(100)

        audioTrack?.stop()
        audioTrack?.flush()
    }

    fun skipToNext() {
        studyJob?.cancel()
        stopPlayback()

        if (_isListening.value) {
            speechRecognizer?.stopListening()
            _isListening.value = false
        }

        if (_currentCardIndex.value < cards.size - 1) {
            _currentCardIndex.value += 1
            _isFlipped.value = (promptSide == "Back")
            _feedbackMessage.value = null

            if (_isPlaying.value || continuousPlay) {
                startStudy(forceRestart = true)
            } else {
                updateNotification("Ready")
            }
        } else {
            _isPlaying.value = false
            updateMediaState(PlaybackState.STATE_PAUSED)
            updateNotification("Session Complete")
        }
    }

    fun skipToPrevious() {
        studyJob?.cancel()
        stopPlayback()

        if (_isListening.value) {
            speechRecognizer?.stopListening()
            _isListening.value = false
        }

        val currentTime = System.currentTimeMillis()
        val isDoublePress = (currentTime - lastRewindTime) < REWIND_THRESHOLD_MS
        lastRewindTime = currentTime

        if (isDoublePress && _currentCardIndex.value > 0) {
            _currentCardIndex.value -= 1
        }

        _isFlipped.value = (promptSide == "Back")
        _feedbackMessage.value = null

        if (_isPlaying.value || continuousPlay) {
            startStudy(forceRestart = true)
        } else {
            updateNotification("Ready")
        }
    }

    fun skipStt() { skipToNext() }

    fun initializeSession(
        sessionCards: List<net.ericclark.studiare.data.Card>,
        frontLanguage: String,
        backLanguage: String,
        startIndex: Int,
        enableStt: Boolean,
        isGraded: Boolean,
        promptSide: String,
        hideAnswerText: Boolean
    ) {
        cards = sessionCards
        frontLanguageStr = frontLanguage
        backLanguageStr = backLanguage
        _currentCardIndex.value = startIndex
        this.enableStt = enableStt
        this.isGraded = isGraded
        this.promptSide = promptSide
        this.hideAnswerText = hideAnswerText

        _isFlipped.value = (promptSide == "Back")

        startForeground(1, buildNotification("Ready to study"))
    }

    fun revealAnswer() {
        _isFlipped.value = (promptSide == "Front")
    }

    fun startStudy(forceRestart: Boolean = false) {
        if (_isPlaying.value && !forceRestart) return
        if (forceRestart) studyJob?.cancel()

        _isPlaying.value = true
        _feedbackMessage.value = null
        updateMediaState(PlaybackState.STATE_PLAYING)
        updateNotification("Audio Session Active")

        studyJob = serviceScope.launch {
            processStudyLoop()
        }
    }

    fun pauseStudy() {
        _isPlaying.value = false
        updateMediaState(PlaybackState.STATE_PAUSED)
        stopPlayback()

        if (_isListening.value) {
            speechRecognizer?.stopListening()
            _isListening.value = false
        }

        studyJob?.cancel()
        updateNotification("Paused")
    }

    private fun stopPlayback() {
        tts?.stop()
        try {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.stop()
            }
            audioTrack?.flush()
        } catch (e: Exception) { Log.e("AudioStudy", "Error stopping track", e) }
    }

    fun resumeStudy() {
        if (!_isPlaying.value) {
            startStudy()
        }
    }

    private suspend fun processStudyLoop() {
        while (_isPlaying.value && _currentCardIndex.value < cards.size) {
            val card = cards[_currentCardIndex.value]
            currentCardSolved = false

            val isFrontFirst = promptSide == "Front"

            val firstText = if (isFrontFirst) card.front else card.back
            val firstNotes = if (isFrontFirst) card.frontNotes else card.backNotes
            val firstLang = if (isFrontFirst) frontLanguageStr else backLanguageStr

            val secondText = if (isFrontFirst) card.back else card.front
            val secondNotes = if (isFrontFirst) card.backNotes else card.frontNotes
            val secondLang = if (isFrontFirst) backLanguageStr else frontLanguageStr

            // 1. Show/Speak FIRST Side (Prompt)
            _isFlipped.value = !isFrontFirst
            _feedbackMessage.value = null
            speakText(firstText, firstNotes, firstLang)

            if (!_isPlaying.value) break

            // 2. Handle Answer (Delay OR STT)
            if (enableStt) {
                if (!hideAnswerText) {
                    _isFlipped.value = isFrontFirst
                }

                var answeredCorrectly = false

                while (!answeredCorrectly && _isPlaying.value) {
                    _feedbackMessage.value = "Listening..."
                    val spokenText = listenAndVerify(secondLang)

                    if (spokenText != null) {
                        if (checkAnswer(spokenText, secondText)) {
                            answeredCorrectly = true
                            _feedbackMessage.value = "Correct!"

                            if (isGraded) {
                                currentCardSolved = true
                                _cardResults.emit(card.id to true)
                            }

                            if (hideAnswerText) {
                                _isFlipped.value = isFrontFirst
                            }

                        } else {
                            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
                            _feedbackMessage.value = "Try Again"
                            delay(1000)
                        }
                    } else {
                        if (_isPlaying.value) delay(500)
                    }
                }
            } else {
                delay(answerDelayMs)
            }

            // 3. Show/Speak SECOND Side (Answer)
            if (!_isPlaying.value) break

            _isFlipped.value = isFrontFirst
            _feedbackMessage.value = null

            speakText(secondText, secondNotes, secondLang)

            if (!_isPlaying.value) break
            delay(nextCardDelayMs)

            // 4. Next Card Logic
            if (!_isPlaying.value) break
            if (_currentCardIndex.value < cards.size - 1) {
                _currentCardIndex.value += 1
                if (!continuousPlay) {
                    _isPlaying.value = false
                    _isFlipped.value = !isFrontFirst
                    updateMediaState(PlaybackState.STATE_PAUSED)
                    updateNotification("Paused")
                }
            } else {
                _isPlaying.value = false
                updateMediaState(PlaybackState.STATE_PAUSED)
                updateNotification("Session Complete")
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private suspend fun listenAndVerify(languageCode: String): String? {
        if (!_isPlaying.value) return null

        // 1. Try Sherpa STT first
        // Check if we can initialize the engine for this language
        // Since getSherpaSttForLang caches the instance, checking here is efficient
        // Note: We run this on IO to avoid blocking
        val sherpaEngine = withContext(Dispatchers.IO) { getSherpaSttForLang(languageCode) }

        if (sherpaEngine != null) {
            return runSherpaStt(sherpaEngine)
        }

        // 2. Fallback to Android System STT
        return runSystemStt(languageCode)
    }

    private suspend fun runSystemStt(languageCode: String): String? = suspendCancellableCoroutine { cont ->
        _isListening.value = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
        }

        serviceScope.launch(Dispatchers.Main) {
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening.value = false }

                override fun onError(error: Int) {
                    _isListening.value = false
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val topMatch = matches?.firstOrNull()
                    if (cont.isActive) cont.resume(topMatch)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }

        cont.invokeOnCancellation {
            _isListening.value = false
            serviceScope.launch(Dispatchers.Main) {
                speechRecognizer?.stopListening()
            }
        }
    }

    /*
    private suspend fun listenAndVerify(languageCode: String): String? = suspendCancellableCoroutine { cont ->
        if (!_isPlaying.value) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        _isListening.value = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
        }

        serviceScope.launch(Dispatchers.Main) {
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening.value = false }

                override fun onError(error: Int) {
                    _isListening.value = false
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val topMatch = matches?.firstOrNull()
                    if (cont.isActive) cont.resume(topMatch)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }

        cont.invokeOnCancellation {
            _isListening.value = false
            serviceScope.launch(Dispatchers.Main) {
                speechRecognizer?.stopListening()
            }
        }
    }
    */


    private fun checkAnswer(spoken: String, expected: String): Boolean {
        val normSpoken = spoken.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        val normExpected = expected.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
        return normSpoken == normExpected
    }

    private suspend fun speakText(text: String, notes: String?, languageCode: String) {
        val rawText = if (notes.isNullOrBlank()) text else "$text. $notes"
        val sanitizedText = rawText.lowercase()

        // 1. Try Sherpa HD TTS
        val sherpaAudio = withContext(Dispatchers.IO) {
            val ttsEngine = getSherpaTtsForLang(languageCode)
            if (ttsEngine != null) {
                Log.i("AudioStudyService", "TTS: Executing Sherpa-Onnx HD TTS for [$languageCode]")
                ttsEngine.generate(sanitizedText, 0, 1.0f)
            } else {
                null
            }
        }

        if (sherpaAudio != null) {
            playSherpaAudio(sherpaAudio.samples, sherpaAudio.sampleRate)
            return
        }

        // 2. Fallback to System TTS
        Log.i("AudioStudyService", "TTS: Falling back to Native Android TTS for [$languageCode]")

        if (!ttsReady || tts == null) {
            Log.e("AudioStudyService", "TTS: Native TTS engine is not ready or null.")
            return
        }

        val locale = Locale(languageCode)
        val result = tts?.isLanguageAvailable(locale)
        if (result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts?.language = locale
        } else {
            Log.w("AudioStudyService", "TTS: Language $languageCode not supported by Native TTS, defaulting to English.")
            tts?.language = Locale.ENGLISH
        }

        // Log the actual system voice model being used
        val currentVoice = tts?.voice
        if (currentVoice != null) {
            Log.i("AudioStudyService", "TTS: Native Model -> Name: ${currentVoice.name}, Locale: ${currentVoice.locale}")
        } else {
            Log.i("AudioStudyService", "TTS: Native Model -> Default (No specific voice info available)")
        }

        val uuid = UUID.randomUUID().toString()
        tts?.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, null, uuid)

        delay(250)

        while (tts?.isSpeaking == true && _isPlaying.value) {
            delay(100)
        }

        if (!_isPlaying.value) {
            tts?.stop()
        }
    }

    private fun updateMediaState(state: Int) {
        val playbackState = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val mediaStyle = android.app.Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous, "Previous",
            PendingIntent.getService(this, 3, Intent(this, AudioStudyService::class.java).setAction("PREV"), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next, "Next",
            PendingIntent.getService(this, 4, Intent(this, AudioStudyService::class.java).setAction("NEXT"), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val playPauseAction = if (_isPlaying.value) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause",
                PendingIntent.getService(this, 1, Intent(this, AudioStudyService::class.java).setAction("PAUSE"), PendingIntent.FLAG_IMMUTABLE)
            ).build()
        } else {
            Notification.Action.Builder(
                android.R.drawable.ic_media_play, "Play",
                PendingIntent.getService(this, 2, Intent(this, AudioStudyService::class.java).setAction("PLAY"), PendingIntent.FLAG_IMMUTABLE)
            ).build()
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Studiare")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.studiare_solid)
            .setContentIntent(pendingIntent)
            .setStyle(mediaStyle)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1, buildNotification(text))
            }
        } else {
            notificationManager.notify(1, buildNotification(text))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Study Playback", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Controls for audio flashcard study"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> resumeStudy()
            "PAUSE" -> pauseStudy()
            "NEXT" -> skipToNext()
            "PREV" -> skipToPrevious()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        toneGenerator.release()
        mediaSession?.release()
        studyJob?.cancel()

        sherpaTts?.release()
        sherpaStt?.release()
        audioTrack?.release()
    }
}

data class SherpaModelConfig(
    val langCode: String,
    val modelUrl: String,
    val modelName: String,
    val type: String, // "TTS" or "STT"
    val size: String
)

object SherpaModelRepo {
    // Base URLs
    private const val TTS_BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
    private const val ASR_BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

    // Estimates: TTS ~50MB, STT ~80MB
    private val availableModels = listOf(
        // --- TTS MODELS (VITS-Piper) ---
        SherpaModelConfig("en", "$TTS_BASE_URL/vits-piper-en_US-amy-low.tar.bz2", "vits-piper-en_US-amy-low", "TTS", "60 MB"),
        SherpaModelConfig("de", "$TTS_BASE_URL/vits-piper-de_DE-thorsten-low.tar.bz2", "vits-piper-de_DE-thorsten-low", "TTS", "60 MB"),
        SherpaModelConfig("es", "$TTS_BASE_URL/vits-piper-es_ES-sharvard-low.tar.bz2", "vits-piper-es_ES-sharvard-low", "TTS", "60 MB"),
        SherpaModelConfig("fr", "$TTS_BASE_URL/vits-piper-fr_FR-siwis-low.tar.bz2", "vits-piper-fr_FR-siwis-low", "TTS", "50 MB"),
        SherpaModelConfig("it", "$TTS_BASE_URL/vits-piper-it_IT-riccardo-x_low.tar.bz2", "vits-piper-it_IT-riccardo-x_low", "TTS", "50 MB"),
        SherpaModelConfig("nl", "$TTS_BASE_URL/vits-piper-nl_BE-nathalie-x_low.tar.bz2", "vits-piper-nl_BE-nathalie-x_low", "TTS", "50 MB"),
        SherpaModelConfig("pl", "$TTS_BASE_URL/vits-piper-pl_PL-darkman-low.tar.bz2", "vits-piper-pl_PL-darkman-low", "TTS", "50 MB"),
        SherpaModelConfig("pt", "$TTS_BASE_URL/vits-piper-pt_BR-faber-low.tar.bz2", "vits-piper-pt_BR-faber-low", "TTS", "50 MB"),
        SherpaModelConfig("ru", "$TTS_BASE_URL/vits-piper-ru_RU-irina-low.tar.bz2", "vits-piper-ru_RU-irina-low", "TTS", "50 MB"),
        SherpaModelConfig("uk", "$TTS_BASE_URL/vits-piper-uk_UA-ukrainian_tts-low.tar.bz2", "vits-piper-uk_UA-ukrainian_tts-low", "TTS", "50 MB"),
        SherpaModelConfig("zh", "$TTS_BASE_URL/vits-piper-zh_CN-huayan-x_low.tar.bz2", "vits-piper-zh_CN-huayan-x_low", "TTS", "50 MB"),
        SherpaModelConfig("ar", "$TTS_BASE_URL/vits-piper-ar_JO-kareem-low.tar.bz2", "vits-piper-ar_JO-kareem-low", "TTS", "50 MB"),
        SherpaModelConfig("ca", "$TTS_BASE_URL/vits-piper-ca_ES-upc_ona-x_low.tar.bz2", "vits-piper-ca_ES-upc_ona-x_low", "TTS", "50 MB"),
        SherpaModelConfig("cs", "$TTS_BASE_URL/vits-piper-cs_CZ-jirka-low.tar.bz2", "vits-piper-cs_CZ-jirka-low", "TTS", "50 MB"),
        SherpaModelConfig("da", "$TTS_BASE_URL/vits-piper-da_DK-talesyntese-low.tar.bz2", "vits-piper-da_DK-talesyntese-low", "TTS", "50 MB"),
        SherpaModelConfig("el", "$TTS_BASE_URL/vits-piper-el_GR-raptis-low.tar.bz2", "vits-piper-el_GR-raptis-low", "TTS", "50 MB"),
        SherpaModelConfig("fi", "$TTS_BASE_URL/vits-piper-fi_FI-harri-low.tar.bz2", "vits-piper-fi_FI-harri-low", "TTS", "50 MB"),
        SherpaModelConfig("hu", "$TTS_BASE_URL/vits-piper-hu_HU-anna-low.tar.bz2", "vits-piper-hu_HU-anna-low", "TTS", "50 MB"),
        SherpaModelConfig("is", "$TTS_BASE_URL/vits-piper-is_IS-bui-low.tar.bz2", "vits-piper-is_IS-bui-low", "TTS", "50 MB"),
        SherpaModelConfig("no", "$TTS_BASE_URL/vits-piper-no_NO-talesyntese-low.tar.bz2", "vits-piper-no_NO-talesyntese-low", "TTS", "50 MB"),
        SherpaModelConfig("ro", "$TTS_BASE_URL/vits-piper-ro_RO-mihai-low.tar.bz2", "vits-piper-ro_RO-mihai-low", "TTS", "50 MB"),
        SherpaModelConfig("sv", "$TTS_BASE_URL/vits-piper-sv_SE-talesyntese-low.tar.bz2", "vits-piper-sv_SE-talesyntese-low", "TTS", "50 MB"),
        SherpaModelConfig("tr", "$TTS_BASE_URL/vits-piper-tr_TR-dfki-low.tar.bz2", "vits-piper-tr_TR-dfki-low", "TTS", "50 MB"),
        SherpaModelConfig("vi", "$TTS_BASE_URL/vits-piper-vi_VN-25hours_single-low.tar.bz2", "vits-piper-vi_VN-25hours_single-low", "TTS", "50 MB"),

        // --- STT MODELS (Streaming Zipformer) ---
        SherpaModelConfig("en", "$ASR_BASE_URL/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2", "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17", "STT", "80 MB"),
        SherpaModelConfig("fr", "$ASR_BASE_URL/sherpa-onnx-streaming-zipformer-fr-2023-04-14.tar.bz2", "sherpa-onnx-streaming-zipformer-fr-2023-04-14", "STT", "80 MB"),
        SherpaModelConfig("zh", "$ASR_BASE_URL/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2", "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20", "STT", "80 MB")
    )

    fun getModelForLanguage(lang: String, type: String): SherpaModelConfig? {
        // ... [logic remains the same]
        var match = availableModels.find { it.langCode == lang && it.type == type }
        if (match != null) return match

        val shortLang = if (lang.contains("_")) lang.substringBefore("_") else lang
        match = availableModels.find { it.langCode == shortLang && it.type == type }
        if (match != null) return match

        return availableModels.find {
            it.modelName.contains("_${lang}_", ignoreCase = true) ||
                    it.modelName.contains("-$lang-", ignoreCase = true) ||
                    it.modelName.contains("_${shortLang}_", ignoreCase = true) ||
                    it.modelName.contains("-$shortLang-", ignoreCase = true)
        }?.takeIf { it.type == type }
    }
}

class SherpaModelDownloader(private val context: Context) {
    private val client = HttpClient(Android)

    suspend fun downloadAndExtractModel(
        config: SherpaModelConfig,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val modelDir = File(context.filesDir, "sherpa_models")
        if (!modelDir.exists()) modelDir.mkdirs()

        val destinationFile = File(modelDir, "${config.modelName}.tar.bz2")
        val finalFolder = File(modelDir, config.modelName)

        if (finalFolder.exists() && finalFolder.isDirectory) {
            onProgress(1.0f)
            return@withContext true
        }

        try {
            // prepareGet allows us to execute the request and stream the body
            client.prepareGet(config.modelUrl).execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                val totalBytes = httpResponse.contentLength() ?: 10_000_000L

                val fileStream = FileOutputStream(destinationFile)
                val bufferSize = 8192
                var bytesCopied = 0L

                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(bufferSize.toLong())
                    while (!packet.isEmpty) {
                        val bytes = packet.readBytes()
                        fileStream.write(bytes)
                        bytesCopied += bytes.size
                        onProgress(bytesCopied.toFloat() / totalBytes)
                    }
                }
                fileStream.close()
            }

            unzipTarBz2(destinationFile, modelDir)
            destinationFile.delete()

            return@withContext true
        } catch (e: Exception) {
            Log.e("SherpaDownloader", "Download failed", e)
            return@withContext false
        }
    }

    private fun unzipTarBz2(tarFile: File, destDir: File) {
        val fin = FileInputStream(tarFile)
        val bin = BufferedInputStream(fin)
        // Uses commons-compress for bz2
        val bzIn = BZip2CompressorInputStream(bin)
        val tarIn = TarArchiveInputStream(bzIn)

        var entry: TarArchiveEntry? = null
        while (tarIn.nextEntry.also { entry = it } != null) {
            val currentEntry = entry ?: break
            val outputFile = File(destDir, currentEntry.name)
            if (currentEntry.isDirectory) {
                if (!outputFile.exists()) outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                val fos = FileOutputStream(outputFile)
                val buffer = ByteArray(1024)
                var len: Int
                while (tarIn.read(buffer).also { len = it } != -1) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
            }
        }
        tarIn.close()
        bzIn.close()
        bin.close()
        fin.close()
    }
}