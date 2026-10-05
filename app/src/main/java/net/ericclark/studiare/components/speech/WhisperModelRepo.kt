package net.ericclark.studiare.components.speech

import android.content.Context
import net.ericclark.studiare.R
import net.ericclark.studiare.SherpaModelConfig
import net.ericclark.studiare.data.StringResourceEnum
import java.io.File

/**
 * The three Whisper model sizes offered for on-device speech recognition (Speech-to-Text,
 * Listen & Speak, Listen & Type — see the Studiare roadmap plan). Unlike the per-language HD
 * TTS voices in [net.ericclark.studiare.SherpaModelRepo], one Whisper model is multilingual
 * (~99 languages), so the user picks a size once rather than a model per deck language.
 *
 * Sizes/URLs verified against the actual `k2-fsa/sherpa-onnx` GitHub release assets (both the
 * `asr-models` release listing and a real download+extract of the `tiny` archive) rather than
 * assumed: the download is a `sherpa-onnx-whisper-<size>.tar.bz2` containing both an fp32 and
 * an int8-quantized encoder/decoder plus a `test_wavs/` sample folder we don't need. We keep
 * only the int8 pair (meaningfully smaller, and accuracy loss versus fp32 is minor for this
 * use case) and delete the rest after extraction — see [pruneUnusedFiles].
 */
// Fields are string-resource IDs, not resolved strings — enum constants are initialized before
// any Android Context/composition exists, so the text has to be resolved lazily by each caller
// (via .asString()/.asString(context), same StringResourceEnum pattern used throughout this app)
// rather than baked in at construction time.
enum class WhisperModelSize(
    val id: String,
    override val labelResId: Int,
    val downloadSizeLabelResId: Int,
    val descriptionResId: Int
) : StringResourceEnum {
    TINY(id = "tiny", labelResId = R.string.whisper_model_tiny, downloadSizeLabelResId = R.string.whisper_model_tiny_size, descriptionResId = R.string.whisper_model_tiny_desc),
    BASE(id = "base", labelResId = R.string.whisper_model_base, downloadSizeLabelResId = R.string.whisper_model_base_size, descriptionResId = R.string.whisper_model_base_desc),
    SMALL(id = "small", labelResId = R.string.whisper_model_small, downloadSizeLabelResId = R.string.whisper_model_small_size, descriptionResId = R.string.whisper_model_small_desc);

    companion object {
        fun fromId(id: String?): WhisperModelSize? = entries.find { it.id == id }
    }
}

data class WhisperModelFiles(val encoder: File, val decoder: File, val tokens: File)

object WhisperModelRepo {
    private const val ASR_BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
    const val VAD_MODEL_URL = "$ASR_BASE_URL/silero_vad.onnx"

    private fun modelFolderName(size: WhisperModelSize) = "sherpa-onnx-whisper-${size.id}"

    /** A [SherpaModelConfig] so this reuses [net.ericclark.studiare.SherpaModelDownloader] as-is. */
    fun downloadConfig(context: Context, size: WhisperModelSize): SherpaModelConfig = SherpaModelConfig(
        langCode = "*", // Not per-language; unused for Whisper.
        modelUrl = "$ASR_BASE_URL/${modelFolderName(size)}.tar.bz2",
        modelName = modelFolderName(size),
        type = "WHISPER",
        size = context.getString(size.downloadSizeLabelResId)
    )

    private fun modelDir(context: Context, size: WhisperModelSize): File =
        File(context.filesDir, "sherpa_models/${modelFolderName(size)}")

    fun vadModelFile(context: Context): File = File(context.filesDir, "sherpa_models/silero_vad.onnx")

    /** The int8 encoder/decoder/tokens files for [size], or null if not (fully) downloaded. */
    fun installedFiles(context: Context, size: WhisperModelSize): WhisperModelFiles? {
        val dir = modelDir(context, size)
        val encoder = File(dir, "${size.id}-encoder.int8.onnx")
        val decoder = File(dir, "${size.id}-decoder.int8.onnx")
        val tokens = File(dir, "${size.id}-tokens.txt")
        return if (encoder.exists() && decoder.exists() && tokens.exists()) {
            WhisperModelFiles(encoder, decoder, tokens)
        } else {
            null
        }
    }

    /**
     * Deletes the fp32 encoder/decoder and sample `test_wavs/` that ship inside the tarball
     * alongside the int8 pair we actually use — otherwise every download keeps ~150 MB of
     * files nothing ever reads.
     */
    fun pruneUnusedFiles(context: Context, size: WhisperModelSize) {
        val dir = modelDir(context, size)
        File(dir, "${size.id}-encoder.onnx").delete()
        File(dir, "${size.id}-decoder.onnx").delete()
        File(dir, "test_wavs").deleteRecursively()
    }

    fun deleteModel(context: Context, size: WhisperModelSize) {
        modelDir(context, size).deleteRecursively()
    }

    fun deleteVadModel(context: Context) {
        vadModelFile(context).delete()
    }
}
