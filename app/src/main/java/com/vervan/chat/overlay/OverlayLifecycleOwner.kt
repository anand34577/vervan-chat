package com.vervan.chat.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The minimum owner trio a [androidx.compose.ui.platform.ComposeView] needs to run *outside* an
 * Activity — hosting Compose in a raw [android.view.WindowManager] overlay (see [BubbleService])
 * otherwise crashes at attach time because Compose looks up the lifecycle/viewmodel/savedstate
 * owners off the view tree and finds none. Driven straight to RESUMED for the overlay's lifetime;
 * there is no real Android lifecycle to mirror here (a Service window is simply shown or removed).
 */
class OverlayLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
