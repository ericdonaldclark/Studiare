package net.ericclark.studiare.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.*
import net.ericclark.studiare.data.StudyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Manages the connection to the AudioStudyService and handles HD Language (Sherpa Onnx) model downloads.
 * Acts as a bridge between the ViewModel (Logic) and the Service/FileSystem (Infrastructure).
 */
class AudioServiceManager(
    private val context: Context,
    private val preferenceManager: net.ericclark.studiare.PreferenceManager,
    private val viewModelScope: CoroutineScope,
    private val getCurrentStudyState: () -> net.ericclark.studiare.data.StudyState?,
    private val onAudioProgressUpdate: (Int) -> Unit
) {
    private val TAG = "AudioServiceManager"
    private val sherpaDownloader =
        SherpaModelDownloader(context)

    // Service State
    private var audioService: net.ericclark.studiare.AudioStudyService? = null

    // We use a StateFlow for binding status to react in UI if needed,
    // though ViewModel currently uses a mutableStateOf. We will expose a flow here
    // and let the ViewModel bridge it to Compose state.
    private val _isAudioServiceBound = MutableStateFlow(false)
    val isAudioServiceBound: StateFlow<Boolean> = _isAudioServiceBound

    // Exposed Service State Flows
    private val _audioCardIndex = MutableStateFlow(0)
    val audioCardIndex: StateFlow<Int> = _audioCardIndex

    private val _audioIsFlipped = MutableStateFlow(false)
    val audioIsFlipped: StateFlow<Boolean> = _audioIsFlipped

    private val _audioIsPlaying = MutableStateFlow(false)
    val audioIsPlaying: StateFlow<Boolean> = _audioIsPlaying

    private val _audioFeedback = MutableStateFlow<String?>(null)
    val audioFeedback: StateFlow<String?> = _audioFeedback

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as net.ericclark.studiare.AudioStudyService.LocalBinder
            val boundService = binder.getService()
            audioService = boundService
            _isAudioServiceBound.value = true

            // Initialize if starting fresh or if service is idle
            getCurrentStudyState()?.let { state ->
                val isServiceIdle = boundService.isPlaying.value == false
                val isServiceFresh = boundService.cards.isNullOrEmpty() == true

                if (isServiceFresh || isServiceIdle) {
                    boundService.initializeSession(
                        sessionCards = state.shuffledCards,
                        frontLanguage = state.deckWithCards.deck.frontLanguage,
                        backLanguage = state.deckWithCards.deck.backLanguage,
                        startIndex = state.currentCardIndex,
                        promptSide = state.quizPromptSide,
                        playbackSpeed = state.audioPlaybackSpeed,
                        replayCount = state.audioReplayCount,
                        autoAdvance = state.audioAutoAdvance,
                        answerDelaySeconds = state.audioAnswerDelaySeconds,
                        nextCardDelaySeconds = state.audioNextCardDelaySeconds
                    )
                } else {
                    // Service is actively playing. Update local storage to match it.
                    val serviceIndex = boundService.currentCardIndex.value
                    if (serviceIndex != state.currentCardIndex) {
                        onAudioProgressUpdate(serviceIndex)
                    }
                }
            }

            // Sync Service State to Manager Flows
            viewModelScope.launch {
                boundService.currentCardIndex.collect { index ->
                    _audioCardIndex.value = index
                    onAudioProgressUpdate(index)
                }
            }
            viewModelScope.launch {
                boundService.isFlipped.collect { _audioIsFlipped.value = it }
            }
            viewModelScope.launch {
                boundService.isPlaying.collect { _audioIsPlaying.value = it }
            }
            viewModelScope.launch {
                boundService.feedbackMessage.collect { _audioFeedback.value = it }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            audioService = null
            _isAudioServiceBound.value = false
        }
    }

    fun bindAudioService() {
        val intent = Intent(context, AudioStudyService::class.java)
        context.startService(intent) // Start it so it promotes to FG
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindAudioService() {
        if (_isAudioServiceBound.value) {
            context.unbindService(serviceConnection)
            _isAudioServiceBound.value = false
        }
        // Also stop the service if we are leaving the Audio mode entirely
        val intent = Intent(context, AudioStudyService::class.java)
        context.stopService(intent)
    }

    // Audio Control Methods
    fun toggleAudioPlayPause() {
        if (_audioIsPlaying.value) {
            audioService?.pauseStudy()
        } else {
            audioService?.resumeStudy()
        }
    }

    fun skipAudioNext() {
        audioService?.skipToNext()
    }

    fun skipAudioPrevious() {
        audioService?.skipToPrevious()
    }

    fun setAudioContinuousPlay(enabled: Boolean) {
        audioService?.continuousPlay = enabled
    }

    fun setAudioPlaybackSpeed(speed: Float) {
        audioService?.playbackSpeed = speed
    }

    fun setAudioReplayCount(count: Int) {
        audioService?.replayCount = count
    }

    fun updateAudioDelays(answerDelaySeconds: Double, nextCardDelaySeconds: Double) {
        audioService?.answerDelayMs = (answerDelaySeconds * 1000).toLong()
        audioService?.nextCardDelayMs = (nextCardDelaySeconds * 1000).toLong()
    }

    // --- HD Audio / Sherpa-Onnx Support ---

    fun setHdAudioPrompted(prompted: Boolean = true) {
        viewModelScope.launch {
            preferenceManager.setHdAudioPrompted(prompted)
        }
    }

    fun getUniqueDeckLanguages(allDecks: List<net.ericclark.studiare.data.DeckWithCards>): List<String> {
        val languages = mutableSetOf<String>()
        allDecks.forEach {
            languages.add(it.deck.frontLanguage)
            languages.add(it.deck.backLanguage)
        }
        return languages.filter { it.isNotBlank() }.sorted()
    }

    fun getFormattedModelSize(langCode: String): String {
        var sizeMb = 0
        val tts = SherpaModelRepo.getModelForLanguage(langCode, "TTS")

        tts?.size?.let {
            val num = it.replace(" MB", "").trim().toIntOrNull() ?: 0
            sizeMb += num
        }

        return if (sizeMb > 0) "$sizeMb MB" else "Unknown"
    }

    fun startHdLanguageDownload(languages: List<String>) {
        setHdAudioPrompted(true)
        Toast.makeText(context, context.getString(R.string.downloading_in_background), Toast.LENGTH_SHORT).show()

        viewModelScope.launch(Dispatchers.IO) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "HD_Language_Download"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(channelId, "Language Downloads", android.app.NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcherstudiare)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            val successfulDownloads = mutableListOf<String>()

            // Identify models to download. STT models are retired (speech recognition now runs
            // through the Whisper-based SpeechRecognitionEngine instead) — only TTS voices are
            // fetched here going forward.
            for (langCode in languages) {
                val config = SherpaModelRepo.getModelForLanguage(langCode, "TTS")
                if (config != null) {
                    // Update Notification
                    builder.setContentTitle(context.getString(R.string.downloading_language_format, Locale(langCode).displayLanguage))
                    builder.setContentText(context.getString(R.string.getting_tts_model))
                    builder.setProgress(0, 0, true) // Indeterminate start
                    notificationManager.notify(999, builder.build())

                    val success = sherpaDownloader.downloadAndExtractModel(config) { progress ->
                        // Update progress: progress is 0.0 to 1.0
                        builder.setProgress(100, (progress * 100).toInt(), false)
                        notificationManager.notify(999, builder.build())
                    }

                    if (success && !successfulDownloads.contains(langCode)) {
                        successfulDownloads.add(langCode)
                    }
                }
            }

            // Save preference
            if (successfulDownloads.isNotEmpty()) {
                preferenceManager.addDownloadedHdLanguages(successfulDownloads)
            }

            // Cleanup Notification
            builder.setContentTitle(context.getString(R.string.download_complete))
            builder.setContentText(context.getString(R.string.finished_downloading_models))
            builder.setProgress(0, 0, false)
            builder.setOngoing(false)
            notificationManager.notify(999, builder.build())

            delay(3000)
            notificationManager.cancel(999)
        }
    }

    fun deleteHdLanguage(language: String, onToastMessage: (String) -> Unit) {
        onToastMessage("Deleting model...")

        viewModelScope.launch(Dispatchers.IO) {
            val modelDir = File(context.filesDir, "sherpa_models")
            val types = listOf("TTS", "STT")

            types.forEach { type ->
                val config = SherpaModelRepo.getModelForLanguage(language, type)
                if (config != null) {
                    val folder = File(modelDir, config.modelName)
                    if (folder.exists()) {
                        folder.deleteRecursively()
                    }
                }
            }

            preferenceManager.removeDownloadedHdLanguage(language)
            withContext(Dispatchers.Main) {
                onToastMessage("Deleted ${Locale(language).displayLanguage} model")
            }
        }
    }

    fun deleteAllHdLanguages(onToastMessage: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onToastMessage("Deleting all models...") }

            val modelDir = File(context.filesDir, "sherpa_models")
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }

            preferenceManager.clearDownloadedHdLanguages()
            withContext(Dispatchers.Main) { onToastMessage("All models deleted") }
        }
    }

    // --- Whisper Speech Recognition Support ---
    // One multilingual model per size (not per-language, unlike the HD voices above), so this
    // downloads/deletes by WhisperModelSize instead of by language code.

    /** Returns the download [Job] so a caller can [kotlinx.coroutines.Job.cancel] it mid-download. */
    fun startWhisperModelDownload(
        size: net.ericclark.studiare.components.speech.WhisperModelSize,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ): kotlinx.coroutines.Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val config = net.ericclark.studiare.components.speech.WhisperModelRepo.downloadConfig(context, size)

            // Reserve the last 10% of progress for the small VAD file so the bar doesn't sit
            // at 100% while it's still fetching.
            val modelOk = sherpaDownloader.downloadAndExtractModel(config) { progress ->
                onProgress(progress * 0.9f)
            }

            var success = modelOk
            if (modelOk) {
                net.ericclark.studiare.components.speech.WhisperModelRepo.pruneUnusedFiles(context, size)

                val vadFile = net.ericclark.studiare.components.speech.WhisperModelRepo.vadModelFile(context)
                success = sherpaDownloader.downloadFile(
                    net.ericclark.studiare.components.speech.WhisperModelRepo.VAD_MODEL_URL,
                    vadFile
                ) { progress -> onProgress(0.9f + progress * 0.1f) }
            }

            if (success) {
                preferenceManager.setWhisperModelSize(size.id)
            }
            withContext(Dispatchers.Main) { onComplete(success) }
        }
    }

    fun deleteWhisperModel(
        size: net.ericclark.studiare.components.speech.WhisperModelSize,
        onToastMessage: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            net.ericclark.studiare.components.speech.WhisperModelRepo.deleteModel(context, size)
            preferenceManager.setWhisperModelSize(null)
            val message = context.getString(R.string.deleted_whisper_model_format, context.getString(size.labelResId))
            withContext(Dispatchers.Main) { onToastMessage(message) }
        }
    }
}