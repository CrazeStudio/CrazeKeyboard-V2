package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.settings.KeyboardPreferences

@Composable
fun TutorialScreen(
    preferences: KeyboardPreferences,
    onEnableClicked: () -> Unit,
    onSwitchClicked: () -> Unit,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var testText by remember { mutableStateOf("") }

    val steps = listOf(
        Triple("Welcome to Cboard", "Ultra-fast smart keyboard with real mechanical switch acoustics, Local AI Contextual Emojis, open GIFs & customizable themes.", R.drawable.ic_sparkle),
        Triple("Step 1: Enable Cboard", "Tap the button below to turn on Cboard in your Android Keyboard & Input Method settings.", R.drawable.ic_settings),
        Triple("Step 2: Switch Default Keyboard", "Select Cboard as your active default input method to use it in all your apps.", R.drawable.ic_check_circle),
        Triple("You're All Set!", "Try typing in the test box below to test your new mechanical keyboard experience!", R.drawable.ic_check)
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                for (i in steps.indices) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .height(6.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            // Main Step Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = steps[step].third),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = steps[step].first,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = steps[step].second,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Action buttons for specific steps
                when (step) {
                    1 -> {
                        Button(
                            onClick = onEnableClicked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("1. Enable in Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    2 -> {
                        Button(
                            onClick = onSwitchClicked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("2. Switch Input Method", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    3 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = testText,
                                onValueChange = { testText = it },
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 16.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (testText.isEmpty()) {
                                        Text(
                                            "Tap here to test typing with Cboard...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Flow Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        if (step < steps.size - 1) {
                            step++
                        } else {
                            preferences.updateFirstRun(false)
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .weight(if (step > 0) 2f else 1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (step < steps.size - 1) "Continue" else "Get Started",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
