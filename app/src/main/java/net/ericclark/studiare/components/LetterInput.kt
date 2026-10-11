package net.ericclark.studiare.components

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * An invisible view that receives typed letters and backspaces from the on-screen (or hardware)
 * keyboard. It is a real text-editor view, but reports the visible-password/no-suggestions input
 * type so keyboards don't show predictive text or their clipboard strip, and it never holds any
 * text, so an edit is always reported exactly once (retyping the same letter always works).
 */
class LetterInputView(context: Context) : View(context) {
    var onText: (String) -> Unit = {}
    var onBackspace: () -> Unit = {}
    var onDone: () -> Unit = {}

    /** When true, the keyboard gets no autocorrect or predictive text. Changing it restarts the keyboard so it takes effect. */
    var disableAutocorrect: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            context.getSystemService(InputMethodManager::class.java)?.restartInput(this)
        }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = if (disableAutocorrect) {
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) onText(text.toString())
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                onBackspace()
                return true
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                onDone()
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event)
                return true
            }
        }
    }

    // Hardware keyboards deliver key events straight to the focused view.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (handleKey(event)) true else super.onKeyDown(keyCode, event)

    private fun handleKey(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DEL) {
            onBackspace()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            onDone()
            return true
        }
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        val ch = event.unicodeChar
        if (ch > 0 && event.keyCode != KeyEvent.KEYCODE_ENTER) {
            onText(String(Character.toChars(ch)))
            return true
        }
        return false
    }
}

class LetterInputController {
    internal var view: LetterInputView? = null

    private var lastShowMs = 0L

    /** Hides the keyboard. A tap that also just asked to show it (the answer area) wins over the tap-outside handler. */
    fun hide(force: Boolean = false) {
        val v = view ?: return
        if (!force && android.os.SystemClock.uptimeMillis() - lastShowMs < 300) return
        v.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    fun show() {
        val v = view ?: return
        lastShowMs = android.os.SystemClock.uptimeMillis()
        v.requestFocus()
        val imm = v.context.getSystemService(InputMethodManager::class.java) ?: return
        // After the keyboard was dismissed the view is still focused, so restart the input session first.
        imm.restartInput(v)
        imm.showSoftInput(v, 0)
    }
}

@Composable
fun rememberLetterInputController() = remember { LetterInputController() }

@Composable
fun LetterInput(
    controller: LetterInputController,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
    disableAutocorrect: Boolean = true
) {
    // A Compose text field hides the keyboard when it leaves the screen; a native view doesn't, so do it here.
    val hostView = androidx.compose.ui.platform.LocalView.current
    val createdView = remember { arrayOfNulls<LetterInputView>(1) }
    DisposableEffect(controller) {
        onDispose {
            // Only clear the controller if it still points at this view: a replacement view may already be set.
            if (controller.view === createdView[0]) controller.view = null
            hostView.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(hostView.windowToken, 0)
        }
    }
    AndroidView(
        factory = { context -> LetterInputView(context).also { createdView[0] = it; controller.view = it } },
        update = {
            it.onText = onText
            it.onBackspace = onBackspace
            it.onDone = onDone
            it.disableAutocorrect = disableAutocorrect
        },
        modifier = modifier
    )
}
