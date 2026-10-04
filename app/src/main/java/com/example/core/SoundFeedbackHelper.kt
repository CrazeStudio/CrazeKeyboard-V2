package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.example.R

/** Lightweight sound feedback. Audio resources are loaded only when first requested. */
enum class MechanicalSwitchType(val id: String, val displayName: String, val description: String) {
    IPHONE("iphone", "iPhone iOS Click", "Authentic iOS acoustic keyboard tap sound"),
    CLICK("click", "Crisp Click", "Ultra-crisp, sharp acoustic mechanical click"),
    THOCK("thock", "Creamy Thock", "Deep, rich, low-frequency wooden bottom-out sound"),
    BLUE("blue", "Cherry MX Blue", "Crisp, tactile click with sharp audible feedback"),
    BROWN("brown", "Cherry MX Brown", "Smooth, subtle tactile bump with balanced acoustic"),
    TYPEWRITER("typewriter", "Vintage Typewriter", "Classic metallic mechanical clack"),
    POP("pop", "Marbly Pop", "Modern popping creamy switch sound")
}

class SoundFeedbackHelper private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile private var soundPool: SoundPool? = null
    private val loadedSounds = HashMap<String, Int>(7)
    private val loadLock = Any()

    @Volatile
    private var cachedSysRatio = 1.0f

    private fun ensurePool(): SoundPool? {
        soundPool?.let { return it }
        synchronized(loadLock) {
            soundPool?.let { return it }
            return try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attributes)
                    .build()
                    .also { soundPool = it }
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun resourceFor(type: String): Int = when (type.lowercase()) {
        "iphone", "ios" -> R.raw.mech_iphone
        "click" -> R.raw.mech_click
        "thock" -> R.raw.mech_thock
        "blue" -> R.raw.mech_blue
        "brown" -> R.raw.mech_brown
        "typewriter" -> R.raw.mech_typewriter
        "pop" -> R.raw.mech_pop
        else -> R.raw.mech_iphone
    }

    private fun soundIdFor(type: String): Int {
        val key = type.lowercase()
        synchronized(loadLock) {
            loadedSounds[key]?.let { return it }
            val pool = ensurePool() ?: return 0
            return try {
                val id = pool.load(appContext, resourceFor(key), 1)
                loadedSounds[key] = id
                id
            } catch (_: Throwable) {
                0
            }
        }
    }

    fun refreshVolume() {
        try {
            val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
            cachedSysRatio = if (max > 0) (current.toFloat() / max).coerceIn(0.2f, 1f) else 1f
        } catch (_: Throwable) {
            cachedSysRatio = 1f
        }
    }

    fun playKeyPress(
        switchType: String = "iphone",
        volumePercent: Int = 85,
        isSoundEnabled: Boolean = true
    ) {
        if (!isSoundEnabled || volumePercent <= 0) return
        try {
            val soundId = soundIdFor(switchType)
            if (soundId <= 0) return
            val userGain = (volumePercent / 100f).coerceIn(0f, 1f)
            val finalVolume = (cachedSysRatio * userGain).coerceIn(0.05f, 1f)
            soundPool?.play(soundId, finalVolume, finalVolume, 1, 0, 0.99f + (Math.random().toFloat() * 0.02f))
        } catch (_: Throwable) {
            // Audio failure must never affect typing.
        }
    }

    fun playTestSound(switchType: String, volumePercent: Int = 80) =
        playKeyPress(switchType, volumePercent, true)

    fun release() {
        synchronized(loadLock) {
            try { soundPool?.release() } catch (_: Throwable) {}
            soundPool = null
            loadedSounds.clear()
        }
    }

    companion object {
        @Volatile private var instance: SoundFeedbackHelper? = null

        fun getInstance(context: Context): SoundFeedbackHelper =
            instance ?: synchronized(this) {
                instance ?: SoundFeedbackHelper(context.applicationContext).also { instance = it }
            }
    }
}
