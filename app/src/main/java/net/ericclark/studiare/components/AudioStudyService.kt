package net.ericclark.studiare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.statement.bodyAsChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import io.ktor.client.request.prepareGet
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import android.media.AudioAttributes
import net.ericclark.studiare.components.speech.SpeechEngine
import net.ericclark.studiare.components.speech.SpeechResult
import net.ericclark.studiare.data.*

/**
 * Pure front/back playback with media-style controls (no STT, no grading — that behavior moved to
 * the Whisper-based `TYPED_LISTEN`/`SPOKEN_LISTEN` modes, which share [SpeechEngine] for TTS but
 * use `SpeechRecognitionEngine` for recognition instead of the retired Sherpa-streaming-zipformer
 * and Android `SpeechRecognizer` STT paths this service used to run itself).
 */
class AudioStudyService : android.app.Service() {

    private var lastRewindTime: Long = 0
    private val REWIND_THRESHOLD_MS = 2000L
    private val binder = LocalBinder()

    // Text-to-speech (Sherpa HD voice, falling back to system TTS) lives in SpeechEngine —
    // see its own doc comment for why (shared with the new speech-based study modes, and
    // it's where the anti-hang timeout lives).
    private lateinit var speechEngine: SpeechEngine

    private var mediaSession: MediaSession? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var studyJob: Job? = null

    // State exposed to ViewModel/UI
    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage

    // Configuration
    var cards: List<net.ericclark.studiare.data.Card> = emptyList()
    var frontLanguageStr: String = Locale.getDefault().language
    var backLanguageStr: String = Locale.getDefault().language

    var promptSide: CardSide = CardSide.FRONT

    // Delays in Milliseconds
    var answerDelayMs: Long = 2000L
    var nextCardDelayMs: Long = 2000L

    // Continuous Play Toggle
    var continuousPlay: Boolean = true

    private val CHANNEL_ID = "AudioStudyChannel"

    inner class LocalBinder : android.os.Binder() {
        fun getService(): AudioStudyService = this@AudioStudyService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        speechEngine = SpeechEngine(this)
        setupMediaSession()
        createNotificationChannel()
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

    fun skipToNext() {
        studyJob?.cancel()
        stopPlayback()

        if (_currentCardIndex.value < cards.size - 1) {
            _currentCardIndex.value += 1
            _isFlipped.value = (promptSide == CardSide.BACK)
            _feedbackMessage.value = null

            if (_isPlaying.value || continuousPlay) {
                startStudy(forceRestart = true)
            } else {
                updateNotification(getString(R.string.audio_status_ready))
            }
        } else {
            _isPlaying.value = false
            updateMediaState(PlaybackState.STATE_PAUSED)
            updateNotification(getString(R.string.session_complete))
        }
    }

    fun skipToPrevious() {
        studyJob?.cancel()
        stopPlayback()

        val currentTime = System.currentTimeMillis()
        val isDoublePress = (currentTime - lastRewindTime) < REWIND_THRESHOLD_MS
        lastRewindTime = currentTime

        if (isDoublePress && _currentCardIndex.value > 0) {
            _currentCardIndex.value -= 1
        }

        _isFlipped.value = (promptSide == CardSide.BACK)
        _feedbackMessage.value = null

        if (_isPlaying.value || continuousPlay) {
            startStudy(forceRestart = true)
        } else {
            updateNotification(getString(R.string.audio_status_ready))
        }
    }

    fun initializeSession(
        sessionCards: List<Card>,
        frontLanguage: String,
        backLanguage: String,
        startIndex: Int,
        promptSide: CardSide
    ) {
        cards = sessionCards
        frontLanguageStr = frontLanguage
        backLanguageStr = backLanguage
        _currentCardIndex.value = startIndex
        this.promptSide = promptSide

        _isFlipped.value = (promptSide == CardSide.BACK)

        startForeground(1, buildNotification(getString(R.string.audio_status_ready_to_study)))
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            pauseStudy()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    fun startStudy(forceRestart: Boolean = false) {
        if (_isPlaying.value && !forceRestart) return
        if (forceRestart) studyJob?.cancel()

        if (!requestAudioFocus()) {
            Log.w("AudioStudyService", "Audio focus request denied; playing anyway")
        }

        _isPlaying.value = true
        _feedbackMessage.value = null
        updateMediaState(PlaybackState.STATE_PLAYING)
        updateNotification(getString(R.string.audio_status_active))

        studyJob = serviceScope.launch {
            processStudyLoop()
        }
    }

    fun pauseStudy() {
        _isPlaying.value = false
        updateMediaState(PlaybackState.STATE_PAUSED)
        stopPlayback()
        abandonAudioFocus()

        studyJob?.cancel()
        updateNotification(getString(R.string.audio_status_paused))
    }

    private fun stopPlayback() {
        speechEngine.stop()
    }

    fun resumeStudy() {
        if (!_isPlaying.value) {
            startStudy()
        }
    }

    private suspend fun processStudyLoop() {
        while (_isPlaying.value && _currentCardIndex.value < cards.size) {
            val card = cards[_currentCardIndex.value]

            val isFrontFirst = promptSide == CardSide.FRONT

            val firstText = if (isFrontFirst) card.front else card.back
            val firstNotesList = if (isFrontFirst) card.frontNotes else card.backNotes
            val firstNotes = firstNotesList.filter { it.type == MediaType.PLAIN_TEXT || it.type == MediaType.RICH_TEXT }
                .joinToString(". ") { it.content.replace(Regex("<[^>]*>"), "") } // Strip HTML
            val firstLang = if (isFrontFirst) frontLanguageStr else backLanguageStr

            val secondText = if (isFrontFirst) card.back else card.front
            val secondNotesList = if (isFrontFirst) card.backNotes else card.frontNotes
            val secondNotes = secondNotesList.filter { it.type == MediaType.PLAIN_TEXT || it.type == MediaType.RICH_TEXT }
                .joinToString(". ") { it.content.replace(Regex("<[^>]*>"), "") } // Strip HTML
            val secondLang = if (isFrontFirst) backLanguageStr else frontLanguageStr

            // 1. Show/Speak FIRST Side (Prompt)
            _isFlipped.value = !isFrontFirst
            _feedbackMessage.value = null
            speakText(firstText, firstNotes, firstLang)

            if (!_isPlaying.value) break

            // 2. Pause before revealing the answer
            delay(answerDelayMs)

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
                    updateNotification(getString(R.string.audio_status_paused))
                }
            } else {
                _isPlaying.value = false
                updateMediaState(PlaybackState.STATE_PAUSED)
                updateNotification(getString(R.string.session_complete))
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private suspend fun speakText(text: String, notes: String?, languageCode: String) {
        val result = speechEngine.speak(text, notes, languageCode, shouldContinue = { _isPlaying.value })
        if (result is SpeechResult.Failed) {
            Log.e("AudioStudyService", "speakText failed: ${result.reason}")
            _feedbackMessage.value = getString(R.string.audio_tts_unavailable)
            pauseStudy()
        }
    }

    private fun updateMediaState(state: Int) {
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
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
            android.R.drawable.ic_media_previous, getString(R.string.previous),
            PendingIntent.getService(this, 3, Intent(this, AudioStudyService::class.java).setAction("PREV"), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next, getString(R.string.next),
            PendingIntent.getService(this, 4, Intent(this, AudioStudyService::class.java).setAction("NEXT"), PendingIntent.FLAG_IMMUTABLE)
        ).build()

        val playPauseAction = if (_isPlaying.value) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_pause, getString(R.string.pause),
                PendingIntent.getService(this, 1, Intent(this, AudioStudyService::class.java).setAction("PAUSE"), PendingIntent.FLAG_IMMUTABLE)
            ).build()
        } else {
            Notification.Action.Builder(
                android.R.drawable.ic_media_play, getString(R.string.play),
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
            .setContentTitle(getString(R.string.app_name))
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
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            channel.description = getString(R.string.notification_channel_desc)
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
        speechEngine.release()
        mediaSession?.release()
        abandonAudioFocus()
        studyJob?.cancel()
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

    /**
     * Downloads a single plain file (no archive/extraction) to [destinationFile] — used for
     * the Silero VAD model (`silero_vad.onnx`), which ships as one .onnx file rather than the
     * `.tar.bz2` bundles [downloadAndExtractModel] handles.
     */
    suspend fun downloadFile(
        url: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (destinationFile.exists() && destinationFile.length() > 0L) {
            onProgress(1.0f)
            return@withContext true
        }

        try {
            destinationFile.parentFile?.mkdirs()
            client.prepareGet(url).execute { httpResponse ->
                val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                val totalBytes = httpResponse.contentLength() ?: 1_000_000L

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
            true
        } catch (e: Exception) {
            Log.e("SherpaDownloader", "File download failed", e)
            destinationFile.delete()
            false
        }
    }

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
                        // Download is the first half of overall progress; extraction (below) is
                        // the second half — without this split, a large model's long unpacking
                        // phase previously reported no progress at all after the download finished.
                        onProgress(bytesCopied.toFloat() / totalBytes * 0.5f)
                    }
                }
                fileStream.close()
            }

            unzipTarBz2(destinationFile, modelDir) { extractFraction ->
                onProgress(0.5f + extractFraction * 0.5f)
            }
            destinationFile.delete()

            return@withContext true
        } catch (e: Exception) {
            Log.e("SherpaDownloader", "Download failed", e)
            return@withContext false
        }
    }

    /**
     * [onProgress] is a 0f..1f estimate based on *compressed* bytes consumed from [tarFile],
     * not decompressed output — getting the true uncompressed total would need a first pass
     * over the whole archive, which isn't worth it just for a progress indicator.
     */
    private fun unzipTarBz2(tarFile: File, destDir: File, onProgress: (Float) -> Unit) {
        val totalCompressedBytes = tarFile.length().coerceAtLeast(1L)
        var compressedBytesRead = 0L

        val fin = FileInputStream(tarFile)
        val countingIn = object : FilterInputStream(fin) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) {
                    compressedBytesRead += n
                    onProgress((compressedBytesRead.toFloat() / totalCompressedBytes).coerceIn(0f, 1f))
                }
                return n
            }
        }
        val bin = BufferedInputStream(countingIn)
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
                val fos = BufferedOutputStream(FileOutputStream(outputFile))
                val buffer = ByteArray(8192)
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
        onProgress(1f)
    }
}