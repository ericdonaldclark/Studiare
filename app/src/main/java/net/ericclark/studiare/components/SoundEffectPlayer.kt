package net.ericclark.studiare.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/** Which feedback tone to play for Listen & Speak's mic flow. */
enum class SoundEffect(val tone: Int, val durationMs: Int) {
    LISTEN_START(ToneGenerator.TONE_PROP_BEEP, 150),
    LISTEN_CORRECT(ToneGenerator.TONE_PROP_ACK, 200),
    LISTEN_INCORRECT(ToneGenerator.TONE_PROP_NACK, 300)
}

/** Plays short system feedback tones (listening started, correct, incorrect) via [ToneGenerator] — no bundled audio assets. */
class SoundEffectPlayer {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 100)

    fun play(effect: SoundEffect) {
        toneGenerator.startTone(effect.tone, effect.durationMs)
    }

    fun release() = toneGenerator.release()
}

@Composable
fun rememberSoundEffectPlayer(): SoundEffectPlayer {
    val player = remember { SoundEffectPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}
