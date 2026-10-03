package com.example.ui.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.HapticFeedbackHelper
import com.example.core.KeyHapticRole
import com.example.core.MechanicalHapticProfile
import com.example.core.SoundFeedbackHelper
import com.example.settings.KeyboardPreferences
import kotlin.math.abs

@Composable
fun MechanicalHapticsScreen(
    preferences: KeyboardPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticHelper = remember { HapticFeedbackHelper.getInstance(context) }
    val soundHelper = remember { SoundFeedbackHelper.getInstance(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Master Toggle Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (preferences.isHapticsEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (preferences.isHapticsEnabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_vibration),
                            contentDescription = "Haptics",
                            tint = if (preferences.isHapticsEnabled)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mechanical Haptic Feedback",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (preferences.isHapticsEnabled)
                                "Simulating physical mechanical switch actuation"
                            else
                                "Vibration feedback disabled",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = preferences.isHapticsEnabled,
                        onCheckedChange = {
                            preferences.setHaptics(it)
                            if (it) {
                                hapticHelper.performMechanicalKeyPress(
                                    role = KeyHapticRole.STANDARD,
                                    profileId = preferences.hapticProfile,
                                    intensityPercent = preferences.hapticIntensityPercent,
                                    isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                                    isHapticsEnabled = true
                                )
                            }
                        }
                    )
                }
            }
        }

        // Hardware Diagnostics Info Chip
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_switch),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Android 12+ Rich VibrationEffect Composition Engine • Multi-stage waveforms"
                        } else if (hapticHelper.hasAmplitudeControl) {
                            "VibrationEffect Waveform Engine with Dynamic Amplitude Scaling"
                        } else {
                            "Hardware Tactile Motor Active"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Interactive Live Mechanical Switch Tester Pad
        item {
            Text(
                text = "INTERACTIVE SWITCH TEST PAD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        item {
            MechanicalSwitchTestPad(
                preferences = preferences,
                hapticHelper = hapticHelper,
                soundHelper = soundHelper
            )
        }

        // Adjustable Intensity Controls
        item {
            Text(
                text = "TACTILE INTENSITY & FORCE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tune),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Actuation Intensity",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${preferences.hapticIntensityPercent}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = preferences.hapticIntensityPercent.toFloat(),
                        onValueChange = { newValue ->
                            preferences.updateHapticIntensityPercent(newValue.toInt())
                        },
                        onValueChangeFinished = {
                            hapticHelper.performMechanicalKeyPress(
                                role = KeyHapticRole.STANDARD,
                                profileId = preferences.hapticProfile,
                                intensityPercent = preferences.hapticIntensityPercent,
                                isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                                isHapticsEnabled = true
                            )
                        },
                        valueRange = 5f..100f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Intensity Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            "Soft (30%)" to 30,
                            "Balanced (65%)" to 65,
                            "Crisp (85%)" to 85,
                            "Max (100%)" to 100
                        )
                        presets.forEach { (label, value) ->
                            val isSelected = preferences.hapticIntensityPercent == value
                            SuggestionChip(
                                onClick = {
                                    preferences.updateHapticIntensityPercent(value)
                                    hapticHelper.performMechanicalKeyPress(
                                        role = KeyHapticRole.STANDARD,
                                        profileId = preferences.hapticProfile,
                                        intensityPercent = value,
                                        isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                                        isHapticsEnabled = true
                                    )
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                border = if (isSelected) SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.primary
                                ) else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Switch Selection Header
        item {
            Text(
                text = "MECHANICAL SWITCH PROFILES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        // List of Mechanical Switch Profiles
        items(MechanicalHapticProfile.allProfiles) { profile ->
            val isSelected = preferences.hapticProfile == profile.id
            MechanicalSwitchCard(
                profile = profile,
                isSelected = isSelected,
                onSelect = {
                    preferences.updateHapticProfile(profile.id)
                    hapticHelper.performMechanicalKeyPress(
                        role = KeyHapticRole.STANDARD,
                        profileId = profile.id,
                        intensityPercent = preferences.hapticIntensityPercent,
                        isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                        isHapticsEnabled = true
                    )
                },
                onTestPress = {
                    hapticHelper.performMechanicalKeyPress(
                        role = KeyHapticRole.STANDARD,
                        profileId = profile.id,
                        intensityPercent = preferences.hapticIntensityPercent,
                        isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                        isHapticsEnabled = true
                    )
                }
            )
        }

        // Advanced Physical Simulation Settings
        item {
            Text(
                text = "PHYSICAL SIMULATION & DYNAMICS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    SettingsToggleItem(
                        icon = Icons.Filled.Keyboard,
                        title = "Wire Stabilizer Simulation",
                        subtitle = "Heavier keycap mass & stabilizer bar feel on Spacebar & Enter",
                        checked = preferences.isStabilizerSimulationEnabled,
                        onCheckedChange = {
                            preferences.setStabilizerSimulation(it)
                            hapticHelper.performMechanicalKeyPress(
                                role = KeyHapticRole.SPACEBAR,
                                profileId = preferences.hapticProfile,
                                intensityPercent = preferences.hapticIntensityPercent,
                                isStabilizerSimulation = it,
                                isHapticsEnabled = true
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsToggleItem(
                        icon = Icons.Filled.Build,
                        title = "Key-Specific Dynamics",
                        subtitle = "Distinct tactile bumps for Shift, Modifiers, and Action keys",
                        checked = preferences.isKeySpecificHapticsEnabled,
                        onCheckedChange = { preferences.setKeySpecificHaptics(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsToggleItem(
                        icon = Icons.Filled.PlayArrow,
                        title = "Synchronize Mechanical Sounds",
                        subtitle = "Play acoustic switch clacks synchronized with haptic actuation",
                        checked = preferences.isSoundEnabled,
                        onCheckedChange = { preferences.setSound(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MechanicalSwitchCard(
    profile: MechanicalHapticProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTestPress: () -> Unit
) {
    val switchColor = Color(profile.colorHex)
    val cardBorderColor by animateColorAsState(
        if (isSelected) switchColor else Color.Transparent,
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(2.dp, cardBorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) switchColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Switch Stem Visual Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(switchColor.copy(alpha = 0.2f))
                    .border(1.5.dp, switchColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Cross stem "+" representation
                Box(
                    modifier = Modifier
                        .size(20.dp, 8.dp)
                        .background(switchColor, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp, 20.dp)
                        .background(switchColor, RoundedCornerShape(2.dp))
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = profile.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${profile.actuationForceG}g force",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${profile.travelMm}mm travel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = switchColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = profile.switchCategory,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = switchColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(selectedColor = switchColor)
                )

                OutlinedButton(
                    onClick = onTestPress,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tap", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MechanicalSwitchTestPad(
    preferences: KeyboardPreferences,
    hapticHelper: HapticFeedbackHelper,
    soundHelper: SoundFeedbackHelper
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Live Switch Actuation Playground",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tap individual keycaps or scrub the cursor slider to test tactile feedback",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Mechanical Keys Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TestKeycap(
                    label = "A",
                    sublabel = "1u Alpha",
                    role = KeyHapticRole.STANDARD,
                    modifier = Modifier.weight(1f),
                    preferences = preferences,
                    hapticHelper = hapticHelper,
                    soundHelper = soundHelper
                )

                TestKeycap(
                    label = "↵ Enter",
                    sublabel = "2.25u Action",
                    role = KeyHapticRole.ACTION,
                    modifier = Modifier.weight(1.3f),
                    preferences = preferences,
                    hapticHelper = hapticHelper,
                    soundHelper = soundHelper
                )

                TestKeycap(
                    label = "⇧ Shift",
                    sublabel = "Modifier",
                    role = KeyHapticRole.MODIFIER,
                    modifier = Modifier.weight(1f),
                    preferences = preferences,
                    hapticHelper = hapticHelper,
                    soundHelper = soundHelper
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Spacebar Keycap (Stabilized)
            TestKeycap(
                label = "──────  SPACEBAR (6.25u Stabilized)  ──────",
                sublabel = "Feel heavier stabilizer inertia",
                role = KeyHapticRole.SPACEBAR,
                modifier = Modifier.fillMaxWidth(),
                preferences = preferences,
                hapticHelper = hapticHelper,
                soundHelper = soundHelper
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Cursor Scrubbing Slider Simulator
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Cursor Scrubbing Micro-Ticks",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Slide finger left/right",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            var dragAccumulator by remember { mutableFloatStateOf(0f) }
            var cursorPosition by remember { mutableIntStateOf(15) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .pointerInput(preferences.isHapticsEnabled) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulator += dragAmount.x
                                if (abs(dragAccumulator) > 24f) {
                                    if (dragAccumulator > 0) {
                                        cursorPosition = (cursorPosition + 1).coerceAtMost(30)
                                    } else {
                                        cursorPosition = (cursorPosition - 1).coerceAtLeast(0)
                                    }
                                    dragAccumulator = 0f
                                    hapticHelper.performSelectionTick(preferences.isHapticsEnabled)
                                    soundHelper.playKeyPress(preferences.soundProfile, preferences.soundVolume / 2, preferences.isSoundEnabled)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Cursor Step: $cursorPosition | [Drag across to feel micro-ticks]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TestKeycap(
    label: String,
    sublabel: String,
    role: KeyHapticRole,
    modifier: Modifier = Modifier,
    preferences: KeyboardPreferences,
    hapticHelper: HapticFeedbackHelper,
    soundHelper: SoundFeedbackHelper
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentView = androidx.compose.ui.platform.LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "keycapScale"
    )

    Surface(
        modifier = modifier
            .height(58.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(preferences.hapticProfile, preferences.hapticIntensityPercent, preferences.isHapticsEnabled, preferences.soundProfile) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        hapticHelper.performMechanicalKeyPress(
                            role = role,
                            profileId = preferences.hapticProfile,
                            intensityPercent = preferences.hapticIntensityPercent,
                            isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                            isHapticsEnabled = preferences.isHapticsEnabled,
                            view = currentView
                        )
                        soundHelper.playKeyPress(
                            switchType = preferences.soundProfile,
                            volumePercent = preferences.soundVolume,
                            isSoundEnabled = preferences.isSoundEnabled
                        )
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onLongPress = {
                        hapticHelper.performLongPressHaptic(preferences.isHapticsEnabled, currentView)
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isPressed)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (isPressed) 2.dp else 1.dp,
            color = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = if (isPressed) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = if (role == KeyHapticRole.SPACEBAR) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sublabel,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
