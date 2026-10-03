package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.example.R

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
    private var soundPool: SoundPool? = null
    
    private var soundIdIphone = 0
    private var soundIdClick = 0
    private var soundIdThock = 0
    private var soundIdBlue = 0
    private var soundIdBrown = 0
    private var soundIdTypewriter = 0
    private var soundIdPop = 0

    // Cached volume ratio to prevent cross-process Binder IPC on every single keypress
    @Volatile
    private var cachedSysRatio = 1.0f

    init {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
                
            val pool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build()
                
            soundPool = pool
            soundIdIphone = pool.load(appContext, R.raw.mech_iphone, 1)
            soundIdClick = pool.load(appContext, R.raw.mech_click, 1)
            soundIdThock = pool.load(appContext, R.raw.mech_thock, 1)
            soundIdBlue = pool.load(appContext, R.raw.mech_blue, 1)
            soundIdBrown = pool.load(appContext, R.raw.mech_brown, 1)
            soundIdTypewriter = pool.load(appContext, R.raw.mech_typewriter, 1)
            soundIdPop = pool.load(appContext, R.raw.mech_pop, 1)
        } catch (_: Exception) {
            // Safe fallback if SoundPool native initialization fails
        }
        refreshVolume()
    }

    fun refreshVolume() {
        try {
            val systemVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1
            val maxSystemVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
            cachedSysRatio = if (maxSystemVolume > 0) {
                (systemVolume.toFloat() / maxSystemVolume.toFloat()).coerceIn(0.2f, 1f)
            } else 1f
        } catch (_: Exception) {
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
            val userGain = (volumePercent.toFloat() / 100f).coerceIn(0f, 1f)
            val finalVolume = (cachedSysRatio * userGain).coerceIn(0.05f, 1f)

            val soundId = when (switchType.lowercase()) {
                "iphone", "ios" -> if (soundIdIphone != 0) soundIdIphone else soundIdClick
                "click" -> if (soundIdClick != 0) soundIdClick else soundIdIphone
                "blue" -> if (soundIdBlue != 0) soundIdBlue else soundIdIphone
                "brown" -> if (soundIdBrown != 0) soundIdBrown else soundIdIphone
                "typewriter" -> if (soundIdTypewriter != 0) soundIdTypewriter else soundIdIphone
                "pop" -> if (soundIdPop != 0) soundIdPop else soundIdIphone
                "thock" -> if (soundIdThock != 0) soundIdThock else soundIdIphone
                else -> if (soundIdIphone != 0) soundIdIphone else soundIdClick
            }

            if (soundId > 0) {
                val pitch = 0.98f + (Math.random().toFloat() * 0.04f)
                soundPool?.play(soundId, finalVolume, finalVolume, 1, 0, pitch)
            }
        } catch (_: Exception) {
            // Fail silently on audio drop to avoid any typing freeze
        }
    }
    
    fun playTestSound(switchType: String, volumePercent: Int = 80) {
        playKeyPress(switchType = switchType, volumePercent = volumePercent, isSoundEnabled = true)
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: SoundFeedbackHelper? = null

        fun getInstance(context: Context): SoundFeedbackHelper {
            return instance ?: synchronized(this) {
                instance ?: SoundFeedbackHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
