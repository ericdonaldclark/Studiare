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
    private val onAudioProgressUpdate: (Int) -> Unit,
    private val onGradingResult: (String, Boolean) -> Unit
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

    private val _audioIsListening = MutableStateFlow(false)
    val audioIsListening: StateFlow<Boolean> = _audioIsListening

    private val _audioFeedback = MutableStateFlow<String?>(null)
    val audioFeedback: StateFlow<String?> = _audioFeedback

    private val _audioWaitingForGrade = MutableStateFlow(false)
    val audioWaitingForGrade: StateFlow<Boolean> = _audioWaitingForGrade

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
                        enableStt = state.enableStt,
                        isGraded = state.isGraded,
                        promptSide = state.quizPromptSide,
                        hideAnswerText = state.hideAnswerText
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
                boundService.isListening.collect { _audioIsListening.value = it }
            }
            viewModelScope.launch {
                boundService.feedbackMessage.collect { _audioFeedback.value = it }
            }

            // NEW: Bind waiting state
            viewModelScope.launch {
                boundService.waitingForGrade.collect { _audioWaitingForGrade.value = it }
            }

            // Collect Grading Results and pass to ViewModel
            viewModelScope.launch {
                boundService.cardResults.collect { (cardId, isCorrect) ->
                    onGradingResult(cardId, isCorrect)
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            audioService = null
            _isAudioServiceBound.value = false
        }
    }

    // New Method
    fun resumeAfterGrade() {
        audioService?.resumeAfterGrade()
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

    fun skipAudioStt() {
        audioService?.skipStt()
    }

    fun setAudioContinuousPlay(enabled: Boolean) {
        audioService?.continuousPlay = enabled
    }

    fun updateAudioDelays(answerDelaySeconds: Double, nextCardDelaySeconds: Double) {
        audioService?.answerDelayMs = (answerDelaySeconds * 1000).toLong()
        audioService?.nextCardDelayMs = (nextCardDelaySeconds * 1000).toLong()
    }

    fun revealAudioAnswer() {
        audioService?.revealAnswer()
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
        val stt = SherpaModelRepo.getModelForLanguage(langCode, "STT")

        tts?.size?.let {
            val num = it.replace(" MB", "").trim().toIntOrNull() ?: 0
            sizeMb += num
        }
        stt?.size?.let {
            val num = it.replace(" MB", "").trim().toIntOrNull() ?: 0
            sizeMb += num
        }

        return if (sizeMb > 0) "$sizeMb MB" else "Unknown"
    }

    fun startHdLanguageDownload(languages: List<String>) {
        setHdAudioPrompted(true)
        Toast.makeText(context, "Downloading in background...", Toast.LENGTH_SHORT).show()

        viewModelScope.launch(Dispatchers.IO) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "HD_Language_Download"

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(channelId, "Language Downloads", android.app.NotificationManager.IMPORTANCE_LOW)
                notificationManager.createNotificationChannel(channel)
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launchermcf)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            val successfulDownloads = mutableListOf<String>()

            // Identify models to download
            for (langCode in languages) {
                val types = listOf("TTS", "STT")
                for (type in types) {
                    val config = SherpaModelRepo.getModelForLanguage(langCode, type)
                    if (config != null) {
                        // Update Notification
                        builder.setContentTitle("Downloading ${Locale(langCode).displayLanguage}")
                        builder.setContentText("Getting $type model...")
                        builder.setProgress(0, 0, true) // Indeterminate start
                        notificationManager.notify(999, builder.build())

                        val success = sherpaDownloader.downloadAndExtractModel(config) { progress ->
                            // Update progress: progress is 0.0 to 1.0
                            builder.setProgress(100, (progress * 100).toInt(), false)
                            notificationManager.notify(999, builder.build())
                        }

                        if (success) {
                            if (!successfulDownloads.contains(langCode)) {
                                successfulDownloads.add(langCode)
                            }
                        } else {
                            // Log error? AppLogger is not passed in, skipping explicit log for now or using standard Log
                        }
                    }
                }
            }

            // Save preference
            if (successfulDownloads.isNotEmpty()) {
                preferenceManager.addDownloadedHdLanguages(successfulDownloads)
            }

            // Cleanup Notification
            builder.setContentTitle("Download Complete")
            builder.setContentText("Finished downloading models.")
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
}