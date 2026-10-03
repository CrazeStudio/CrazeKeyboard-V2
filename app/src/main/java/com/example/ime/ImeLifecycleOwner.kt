package com.example.ime

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class ImeLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var isRestored = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        if (!isRestored) {
            savedStateRegistryController.performRestore(Bundle())
            isRestored = true
        }
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }

    fun onStart() {
        onCreate()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } else if (lifecycleRegistry.currentState == Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    fun onStop() {
        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun onDestroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            onStop()
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            try {
                store.clear()
            } catch (_: Exception) {}
        }
    }
}
