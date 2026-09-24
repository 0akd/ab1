package com.arjun.absolutra.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arjun.absolutra.data.KB_KEY_SPACING_HORIZONTAL_KEY
import com.arjun.absolutra.data.KB_KEY_SPACING_VERTICAL_KEY
import com.arjun.absolutra.data.KB_MARGIN_BOTTOM_KEY
import com.arjun.absolutra.data.KB_MARGIN_LEFT_KEY
import com.arjun.absolutra.data.KB_MARGIN_RIGHT_KEY
import com.arjun.absolutra.data.KB_MARGIN_TOP_KEY
import com.arjun.absolutra.data.KB_ROW_HEIGHT_KEY
import com.arjun.absolutra.data.KB_ROW_SPACING_KEY
import com.arjun.absolutra.data.KB_WIDTH_LEFT_KEY
import com.arjun.absolutra.data.KB_WIDTH_MIDDLE_KEY
import com.arjun.absolutra.data.KB_WIDTH_RIGHT_KEY
import com.arjun.absolutra.data.dataStore
import com.arjun.absolutra.ui.theme.AbsolutraTheme

class AbsolutraKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Compose walks up from the IME window root, not from the ComposeView.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AbsolutraTheme {
                    val prefs by this@AbsolutraKeyboardService.dataStore.data.collectAsState(initial = null)

                    KeyboardLayout(
                        onKeyPress = { text -> currentInputConnection?.commitText(text, 1) },
                        onDelete = { currentInputConnection?.deleteSurroundingText(1, 0) },
                        onEnter = {
                            val action = currentInputEditorInfo?.imeOptions
                                ?: EditorInfo.IME_ACTION_UNSPECIFIED
                            currentInputConnection?.performEditorAction(
                                action and EditorInfo.IME_MASK_ACTION
                            )
                        },
                        marginTop = prefs?.get(KB_MARGIN_TOP_KEY) ?: 8,
                        marginBottom = prefs?.get(KB_MARGIN_BOTTOM_KEY) ?: 8,
                        marginLeft = prefs?.get(KB_MARGIN_LEFT_KEY) ?: 0,
                        marginRight = prefs?.get(KB_MARGIN_RIGHT_KEY) ?: 0,
                        rowSpacing = prefs?.get(KB_ROW_SPACING_KEY) ?: 0,
                        horizontalSpacing = prefs?.get(KB_KEY_SPACING_HORIZONTAL_KEY) ?: 8,
                        verticalSpacing = prefs?.get(KB_KEY_SPACING_VERTICAL_KEY) ?: 8,
                        rowHeight = prefs?.get(KB_ROW_HEIGHT_KEY) ?: 50,
                        widthLeft = prefs?.get(KB_WIDTH_LEFT_KEY) ?: 1f,
                        widthMiddle = prefs?.get(KB_WIDTH_MIDDLE_KEY) ?: 1f,
                        widthRight = prefs?.get(KB_WIDTH_RIGHT_KEY) ?: 1f
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
