package com.example.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Mechanical Keyboard Switch profiles simulating tactile actuation curves,
 * leaf springs, clickbars, and bottom-out landings.
 */
enum class MechanicalHapticProfile(
    val id: String,
    val displayName: String,
    val switchCategory: String,
    val description: String,
    val actuationForceG: Int,
    val travelMm: Float,
    val colorHex: Long
) {
    TACTILE_BROWN(
        id = "tactile_brown",
        displayName = "Tactile Brown (Holy Panda)",
        switchCategory = "Tactile",
        description = "Two-stage tactile bump with subtle actuation resistance and clean bottom-out clack",
        actuationForceG = 55,
        travelMm = 4.0f,
        colorHex = 0xFF8D6E63
    ),
    LINEAR_RED(
        id = "linear_red",
        displayName = "Linear Red (Speed Silver)",
        switchCategory = "Linear",
        description = "Silky smooth downstroke with crisp, instantaneous actuation micro-impulse",
        actuationForceG = 45,
        travelMm = 3.4f,
        colorHex = 0xFFE53935
    ),
    CLICKY_BLUE(
        id = "clicky_blue",
        displayName = "Clicky Blue (Box Jade)",
        switchCategory = "Clicky",
        description = "Sharp mechanical clickbar snap with energetic tactile peak and snap reset",
        actuationForceG = 60,
        travelMm = 3.6f,
        colorHex = 0xFF1E88E5
    ),
    SILENT_LINEAR(
        id = "silent_linear",
        displayName = "Silent Black (Gateron Silent)",
        switchCategory = "Damped",
        description = "Cushioned silicone bumper dampening with soft, muted tactile thud",
        actuationForceG = 50,
        travelMm = 3.8f,
        colorHex = 0xFF455A64
    ),
    TOPRE(
        id = "topre",
        displayName = "Topre Electro-Capacitive",
        switchCategory = "Capacitive",
        description = "Weighted rubber dome resistance collapse with smooth spring rebound",
        actuationForceG = 45,
        travelMm = 4.0f,
        colorHex = 0xFF5E35B1
    ),
    HEAVY_THOCK(
        id = "heavy_thock",
        displayName = "Heavy Thock (Box Navy)",
        switchCategory = "Heavy Tactile",
        description = "Deep, punchy, authoritative bottom-out with high tactile resistance",
        actuationForceG = 75,
        travelMm = 3.6f,
        colorHex = 0xFF0D47A1
    ),
    SYSTEM(
        id = "system",
        displayName = "System Native Haptic",
        switchCategory = "Native",
        description = "Android OS standard keypress tick feedback",
        actuationForceG = 40,
        travelMm = 2.0f,
        colorHex = 0xFF00897B
    );

    companion object {
        val allProfiles = entries.toList()

        fun fromId(id: String): MechanicalHapticProfile {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Key role distinction for physical mass & stabilizer simulation
 */
enum class KeyHapticRole {
    STANDARD,       // Normal alphanumeric keys (1u)
    SPACEBAR,       // 6.25u keycap with wire stabilizer (higher mass & inertia)
    ACTION,         // Enter, Backspace (2u - 2.25u stabilized)
    MODIFIER,       // Shift, Sym, Dev toggle
    SELECTION_TICK, // Micro-ticks for cursor scrubbing
    LONG_PRESS      // Deep double confirmation pulse
}

class HapticFeedbackHelper private constructor(context: Context) {
    private val appContext = context.applicationContext
    
    // Asynchronous background executor for haptic triggering
    private val hapticExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "haptic-executor")
    }

    private val vibrator: Vibrator? = try {
        val legacy = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Api31Helper.getVibrator(appContext) ?: legacy
        } else {
            legacy
        }
    } catch (_: Exception) {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val hasVibrator: Boolean = try {
        vibrator?.hasVibrator() == true
    } catch (_: Exception) {
        false
    }

    val hasAmplitudeControl: Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.hasAmplitudeControl() == true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    private val tickEffect: VibrationEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } catch (_: Exception) {
            null
        }
    } else null

    private val clickEffect: VibrationEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } catch (_: Exception) {
            null
        }
    } else null

    private val heavyClickEffect: VibrationEffect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } catch (_: Exception) {
            null
        }
    } else null

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun safeVibrate(effect: VibrationEffect) {
        try {
            val vib = vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Api31Helper.vibrateWithAttributes(vib, effect)
            } else {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(effect, audioAttributes)
                }
            }
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(effect)
                }
            } catch (_: Exception) {}
        }
    }

    private fun safeVibrateLegacy(durationMs: Long) {
        try {
            val vib = vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs, audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            } catch (_: Exception) {}
        }
    }

    /**
     * Ultra-fast micro-tick for cursor scrubbing, spacebar sliding, and scrubbers
     */
    fun performSelectionTick(isHapticsEnabled: Boolean = true, view: View? = null) {
        if (!isHapticsEnabled) return
        
        hapticExecutor.execute {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && tickEffect != null) {
                    safeVibrate(tickEffect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitude = if (hasAmplitudeControl) 85 else VibrationEffect.DEFAULT_AMPLITUDE
                    safeVibrate(VibrationEffect.createOneShot(10L, amplitude))
                } else {
                    safeVibrateLegacy(10L)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Deep affirmative confirmation for long-press alternate character popup opening
     */
    fun performLongPressHaptic(isHapticsEnabled: Boolean = true, view: View? = null) {
        if (!isHapticsEnabled) return
        
        hapticExecutor.execute {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && heavyClickEffect != null) {
                    safeVibrate(heavyClickEffect)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (hasAmplitudeControl) {
                        val timings = longArrayOf(0, 18, 22, 28)
                        val amplitudes = intArrayOf(0, 190, 0, 250)
                        safeVibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        safeVibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                } else {
                    safeVibrateLegacy(35L)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * High-fidelity mechanical switch haptic actuation simulation.
     */
    fun performMechanicalKeyPress(
        role: KeyHapticRole = KeyHapticRole.STANDARD,
        profileId: String = "system",
        intensityPercent: Int = 65,
        isStabilizerSimulation: Boolean = true,
        isHapticsEnabled: Boolean = true,
        view: View? = null
    ) {
        if (!isHapticsEnabled || intensityPercent <= 0) return

        hapticExecutor.execute {
            val profile = MechanicalHapticProfile.fromId(profileId)
            val normalizedIntensity = (intensityPercent.coerceIn(1, 100).toFloat() / 100f)

            // Stabilizer & Mass simulation multiplier
            val massMultiplier = when (role) {
                KeyHapticRole.SPACEBAR -> if (isStabilizerSimulation) 1.25f else 1.0f
                KeyHapticRole.ACTION -> if (isStabilizerSimulation) 1.15f else 1.0f
                KeyHapticRole.MODIFIER -> 1.05f
                KeyHapticRole.SELECTION_TICK -> 0.6f
                KeyHapticRole.LONG_PRESS -> 1.3f
                KeyHapticRole.STANDARD -> 1.0f
            }

            val baseAmplitude = ((normalizedIntensity * massMultiplier * 210f) + 45f).toInt().coerceIn(40, 255)
            val compositionScale = (normalizedIntensity * massMultiplier).coerceIn(0.1f, 1.0f)

            // Native System Profile: Guaranteed crisp one-shot vibration
            if (profile == MechanicalHapticProfile.SYSTEM) {
                val durationMs = ((normalizedIntensity * 25f) + 20f).toLong().coerceIn(20L, 50L)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null) {
                        val amp = if (hasAmplitudeControl) baseAmplitude else VibrationEffect.DEFAULT_AMPLITUDE
                        safeVibrate(VibrationEffect.createOneShot(durationMs, amp))
                    } else {
                        safeVibrateLegacy(durationMs)
                    }
                } catch (_: Exception) {}
                return@execute
            }

            try {
                // Priority 1: Android 12+ (API 31+) Composition API if supported by hardware
                var isCompositionPlayed = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator != null) {
                    val hasPrimitiveSupport = try {
                        Api31Helper.isPrimitiveSupported(vibrator, profile)
                    } catch (_: Throwable) {
                        false
                    }

                    if (hasPrimitiveSupport && Api31Helper.tryVibrateComposition(vibrator, profile, compositionScale)) {
                        isCompositionPlayed = true
                    }
                }

                if (!isCompositionPlayed) {
                    // Priority 2: API 26+ Multi-stage Waveforms
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAmplitudeControl && vibrator != null) {
                        val effect = createSwitchWaveform(profile, baseAmplitude, role)
                        if (effect != null) {
                            safeVibrate(effect)
                            return@execute
                        }
                    }

                    // Priority 3: Predefined effects (API 29+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator != null) {
                        val predefined = when (profile) {
                            MechanicalHapticProfile.HEAVY_THOCK -> heavyClickEffect ?: clickEffect
                            MechanicalHapticProfile.LINEAR_RED, MechanicalHapticProfile.SILENT_LINEAR -> tickEffect ?: clickEffect
                            else -> clickEffect ?: tickEffect
                        }
                        if (predefined != null) {
                            safeVibrate(predefined)
                            return@execute
                        }
                    }

                    // Priority 4: One-shot fallback (API 26+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null) {
                        val durationMs = when (profile) {
                            MechanicalHapticProfile.HEAVY_THOCK -> 35L
                            MechanicalHapticProfile.TACTILE_BROWN -> 30L
                            MechanicalHapticProfile.CLICKY_BLUE -> 25L
                            MechanicalHapticProfile.LINEAR_RED -> 20L
                            MechanicalHapticProfile.SILENT_LINEAR -> 20L
                            MechanicalHapticProfile.TOPRE -> 30L
                            MechanicalHapticProfile.SYSTEM -> 25L
                        }
                        safeVibrate(VibrationEffect.createOneShot(durationMs, baseAmplitude))
                        return@execute
                    }

                    // Priority 5: Legacy Fallback
                    safeVibrateLegacy(25L)
                }

            } catch (_: Exception) {
            }
        }
    }

    /**
     * Backward-compatible key press method delegating to the mechanical engine
     */
    fun performKeyPress(
        view: View? = null,
        intensityMs: Int = 15,
        isHapticsEnabled: Boolean = true,
        profileId: String = "tactile_brown"
    ) {
        val intensityPercent = (intensityMs.coerceIn(1, 50) * 2).coerceIn(10, 100)
        performMechanicalKeyPress(
            role = KeyHapticRole.STANDARD,
            profileId = profileId,
            intensityPercent = intensityPercent,
            isStabilizerSimulation = true,
            isHapticsEnabled = isHapticsEnabled,
            view = view
        )
    }

    private fun createSwitchWaveform(
        profile: MechanicalHapticProfile,
        baseAmp: Int,
        role: KeyHapticRole
    ): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        return try {
            when (profile) {
                MechanicalHapticProfile.TACTILE_BROWN -> {
                    // Stage 1 (bump): ~5ms @ 45% amp -> Gap 5ms -> Stage 2 (bottom-out): ~11ms @ 100% amp
                    val bumpAmp = (baseAmp * 0.45f).toInt().coerceAtLeast(15)
                    val timings = longArrayOf(0, 5, 5, 11)
                    val amps = intArrayOf(0, bumpAmp, 0, baseAmp)
                    VibrationEffect.createWaveform(timings, amps, -1)
                }
                MechanicalHapticProfile.LINEAR_RED -> {
                    // Pure single micro-impulse ~7ms
                    val redAmp = (baseAmp * 0.75f).toInt().coerceAtLeast(15)
                    val timings = longArrayOf(0, 7)
                    val amps = intArrayOf(0, redAmp)
                    VibrationEffect.createWaveform(timings, amps, -1)
                }
                MechanicalHapticProfile.CLICKY_BLUE -> {
                    // Sharp clickbar peak ~4ms @ 100% -> Gap 3ms -> Reset tick ~5ms @ 50%
                    val tickAmp = (baseAmp * 0.50f).toInt().coerceAtLeast(15)
                    val timings = longArrayOf(0, 4, 3, 5)
                    val amps = intArrayOf(0, baseAmp, 0, tickAmp)
                    VibrationEffect.createWaveform(timings, amps, -1)
                }
                MechanicalHapticProfile.SILENT_LINEAR -> {
                    // Cushion wave: Soft ramped curve
                    val lowAmp = (baseAmp * 0.25f).toInt().coerceAtLeast(10)
                    val midAmp = (baseAmp * 0.45f).toInt().coerceAtLeast(15)
                    val timings = longArrayOf(0, 4, 4, 6, 4)
                    val amps = intArrayOf(0, lowAmp, midAmp, lowAmp, 0)
                    VibrationEffect.createWaveform(timings, amps, -1)
                }
                MechanicalHapticProfile.TOPRE -> {
                    // Dome snap (~8ms high) -> Cushion rebound (~9ms moderate)
                    val domeAmp = (baseAmp * 0.90f).toInt().coerceAtLeast(20)
                    val reboundAmp = (baseAmp * 0.55f).toInt().coerceAtLeast(15)
                    val timings = longArrayOf(0, 8, 4, 9)
                    val amps = intArrayOf(0, domeAmp, 0, reboundAmp)
                    VibrationEffect.createWaveform(timings, amps, -1)
                }
                MechanicalHapticProfile.HEAVY_THOCK -> {
                    // Deep punchy 15ms wave
                    val timings = longArrayOf(0, 15)
                    val amps = intArrayOf(0, baseAmp)
                    VibrationEffect.createWaveform(timings, amps, -1)
                }
                MechanicalHapticProfile.SYSTEM -> {
                    val sysAmp = (baseAmp * 0.60f).toInt().coerceAtLeast(15)
                    val timings = longArrayOf(0, 7)
                    val amps = intArrayOf(0, sysAmp)
                    VibrationEffect.createWaveform(timings, amps, -1)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private object Api31Helper {
        fun getVibrator(context: Context): Vibrator? {
            return try {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } catch (_: Throwable) {
                null
            }
        }

        @Suppress("WrongConstant")
        fun vibrateWithAttributes(vibrator: Vibrator, effect: VibrationEffect) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attr = android.os.VibrationAttributes.Builder()
                        .setUsage(18) // VibrationAttributes.USAGE_IME_FEEDBACK
                        .build()
                    vibrator.vibrate(effect, attr)
                } else {
                    vibrator.vibrate(effect)
                }
            } catch (_: Throwable) {
                try {
                    vibrator.vibrate(effect)
                } catch (_: Throwable) {}
            }
        }

        fun isPrimitiveSupported(vibrator: Vibrator, profile: MechanicalHapticProfile): Boolean {
            return try {
                val testPrim = when (profile) {
                    MechanicalHapticProfile.TACTILE_BROWN -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                    MechanicalHapticProfile.LINEAR_RED -> VibrationEffect.Composition.PRIMITIVE_CLICK
                    MechanicalHapticProfile.CLICKY_BLUE -> VibrationEffect.Composition.PRIMITIVE_CLICK
                    MechanicalHapticProfile.SILENT_LINEAR -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
                    MechanicalHapticProfile.TOPRE -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
                    MechanicalHapticProfile.HEAVY_THOCK -> VibrationEffect.Composition.PRIMITIVE_CLICK
                    MechanicalHapticProfile.SYSTEM -> VibrationEffect.Composition.PRIMITIVE_TICK
                }
                vibrator.areAllPrimitivesSupported(testPrim)
            } catch (_: Throwable) {
                false
            }
        }

        fun tryVibrateComposition(
            vibrator: Vibrator,
            profile: MechanicalHapticProfile,
            scale: Float
        ): Boolean {
            try {
                val composition = VibrationEffect.startComposition()
                val finalScale = scale.coerceIn(0.1f, 1.0f)
                when (profile) {
                    MechanicalHapticProfile.TACTILE_BROWN -> {
                        val bumpScale = (finalScale * 0.55f).coerceIn(0.1f, 1.0f)
                        val bottomOutScale = (finalScale * 0.95f).coerceIn(0.1f, 1.0f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, bumpScale, 0)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, bottomOutScale, 7)
                    }
                    MechanicalHapticProfile.LINEAR_RED -> {
                        val redScale = (finalScale * 0.70f).coerceIn(0.1f, 1.0f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, redScale, 0)
                    }
                    MechanicalHapticProfile.CLICKY_BLUE -> {
                        val clickScale = (finalScale * 1.0f).coerceIn(0.1f, 1.0f)
                        val tickScale = (finalScale * 0.45f).coerceIn(0.1f, 1.0f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, clickScale, 0)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, tickScale, 5)
                    }
                    MechanicalHapticProfile.SILENT_LINEAR -> {
                        val silentScale = (finalScale * 0.50f).coerceIn(0.1f, 1.0f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, silentScale, 0)
                    }
                    MechanicalHapticProfile.TOPRE -> {
                        val domeScale = (finalScale * 0.85f).coerceIn(0.1f, 1.0f)
                        val reboundScale = (finalScale * 0.40f).coerceIn(0.1f, 1.0f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, domeScale, 0)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, reboundScale, 8)
                    }
                    MechanicalHapticProfile.HEAVY_THOCK -> {
                        val thockScale = (finalScale * 1.0f).coerceIn(0.1f, 1.0f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, thockScale, 0)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, (thockScale * 0.75f).coerceIn(0.1f, 1.0f), 5)
                    }
                    MechanicalHapticProfile.SYSTEM -> {
                        val sysScale = (finalScale * 0.65f).coerceIn(0.1f, 1.0f)
                        composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, sysScale, 0)
                    }
                }
                vibrateWithAttributes(vibrator, composition.compose())
                return true
            } catch (_: Throwable) {
                return false
            }
        }
    }

    companion object {
        @Volatile
        private var instance: HapticFeedbackHelper? = null

        fun getInstance(context: Context): HapticFeedbackHelper {
            return instance ?: synchronized(this) {
                instance ?: HapticFeedbackHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
