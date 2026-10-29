package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.ATLCompat
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.SYSTEM_VOICE_INPUT_PACKAGE
import org.futo.inputmethod.latin.uix.TextEditPopupActivity
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.render
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.updates.ConditionalMigrateUpdateNotice
import org.futo.inputmethod.updates.openManualUpdateCheck

val HomeScreenLite = UserSettingsMenu(
    title = R.string.settings_home_title,
    navPath = "home", registerNavPath = false,
    settings = listOf(
        userSettingDecorationOnly {
            val context = LocalContext.current
            if (ATLCompat.IsATL) {
                NavigationItem(
                    title = "Launch keyboard (window)",
                    style = NavigationItemStyle.Misc,
                    navigate = { ATLCompat.launchKeyboard(context, false) }
                )
                NavigationItem(
                    title = "Launch keyboard (layershell)",
                    style = NavigationItemStyle.Misc,
                    navigate = { ATLCompat.launchKeyboard(context, true) }
                )
            }
        },
        userSettingNavigationItem(
            title = R.string.settings_check_for_updates_manually,
            style = NavigationItemStyle.Misc,
            navigate = { nav -> nav.context.openManualUpdateCheck() }
        ),
        userSettingNavigationItem(
            title = R.string.language_settings_title,
            style = NavigationItemStyle.HomePrimary,
            navigateTo = "languages",
            icon = R.drawable.globe
        ),
        userSettingNavigationItem(
            title = R.string.settings_keyboard_typing_title,
            style = NavigationItemStyle.HomeSecondary,
            navigateTo = "keyboardAndTyping",
            icon = R.drawable.keyboard
        ),
        userSettingNavigationItem(
            title = SwipeMenu.title,
            style = NavigationItemStyle.HomePrimary,
            navigateTo = SwipeMenu.navPath,
            icon = R.drawable.swipe_icon
        ),
        userSettingNavigationItem(
            title = R.string.prediction_settings_title,
            style = NavigationItemStyle.HomeTertiary,
            navigateTo = PredictiveTextMenu.navPath,
            icon = R.drawable.text_prediction
        ),
        UserSetting(name = R.string.voice_input_settings_title) {
            val navController = LocalNavController.current
            val useExternal = useDataStoreValue(USE_SYSTEM_VOICE_INPUT)
            val externalPkg = useDataStoreValue(SYSTEM_VOICE_INPUT_PACKAGE)
            val name = usePackageReadableName(externalPkg)
            NavigationItem(
                title = stringResource(R.string.voice_input_settings_title),
                style = NavigationItemStyle.HomePrimary,
                subtitle = if (useExternal && name != null) {
                    stringResource(R.string.voice_input_settings_builtin_disabled_notice2, name)
                } else {
                    null
                },
                navigate = { navController!!.navigate(VoiceInputMenu.navPath) },
                icon = painterResource(R.drawable.mic_fill)
            )
        },
        userSettingNavigationItem(
            title = R.string.action_settings_title,
            style = NavigationItemStyle.HomeSecondary,
            navigateTo = "actions",
            icon = R.drawable.smile
        ),
        userSettingNavigationItem(
            title = R.string.theme_settings_title,
            style = NavigationItemStyle.HomeTertiary,
            navigateTo = "themes",
            icon = R.drawable.themes
        ),
        userSettingNavigationItem(
            title = R.string.payment_screen_short_title,
            style = NavigationItemStyle.HomePrimary,
            navigateTo = "payment",
            icon = R.drawable.dollar_sign,
        ).copy(visibilityCheck = {
            useDataStoreValue(IS_ALREADY_PAID) == false
        }, appearInSearchIfVisibilityCheckFailed = false),
        userSettingNavigationItem(
            title = R.string.help_menu_title,
            style = NavigationItemStyle.HomeSecondary,
            navigateTo = "help",
            icon = R.drawable.help_circle
        ),
        userSettingNavigationItem(
            title = R.string.dev_settings_title,
            style = NavigationItemStyle.HomeTertiary,
            navigateTo = "developer",
            icon = R.drawable.code
        ).copy(visibilityCheck = {
            useDataStoreValue(IS_DEVELOPER) == true || LocalInspectionMode.current
        }),
        userSettingNavigationItem(
            title = R.string.misc_settings_title,
            style = NavigationItemStyle.MiscNoArrow,
            navigateTo = "misc",
        ),
        userSettingNavigationItem(
            title = R.string.credits_menu_title,
            style = NavigationItemStyle.MiscNoArrow,
            navigateTo = "credits",
        ),
    )
)

@Preview(showBackground = true)
@Composable
fun HomeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isPaid = useDataStoreValue(IS_ALREADY_PAID)

    Column {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8068FF), Color(0xFF3CCFC5))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.english_ime_name),
                        modifier = Modifier.size(46.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.english_ime_name), style = Typography.Heading.Medium)
                    Text(
                        stringResource(R.string.craze_dashboard_tagline),
                        style = Typography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { navController.navigate("search") },
                    modifier = Modifier
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.settings_search_menu_title)
                    )
                }
            }

            ConditionalMigrateUpdateNotice()
            // Keep the upstream payment notice prominent and reachable in the new dashboard.
            ConditionalUnpaidNoticeWithNav(navController)

            Card(
                onClick = { navController.navigate("themes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF171629), Color(0xFF382C87), Color(0xFF283C83))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(9.dp),
                            shape = CircleShape,
                            color = Color(0xFFB9F45A)
                        ) { }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            stringResource(R.string.craze_dashboard_eyebrow),
                            style = Typography.Small,
                            color = Color.White.copy(alpha = 0.84f)
                        )
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Text(
                                stringResource(R.string.craze_dashboard_version_label),
                                style = Typography.Small,
                                color = Color(0xFFB9F45A),
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.craze_dashboard_hero_title),
                        style = Typography.Heading.Medium,
                        color = Color.White
                    )
                    Text(
                        stringResource(R.string.craze_dashboard_hero_subtitle),
                        style = Typography.Body.Regular,
                        color = Color(0xFFE1E4FF),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 17.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.craze_dashboard_explore_themes),
                            style = Typography.Body.Medium,
                            color = Color(0xFFB9F45A)
                        )
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFB9F45A), modifier = Modifier.size(18.dp))
                    }
                    CrazeKeyboardPreview()
                }
            }

            Text(
                stringResource(R.string.craze_dashboard_customize_title),
                style = Typography.Heading.RegularMl,
                modifier = Modifier.padding(start = 20.dp, top = 21.dp, bottom = 9.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CrazeQuickAction(
                        R.string.craze_dashboard_themes_title,
                        R.string.craze_dashboard_themes_subtitle,
                        R.drawable.themes,
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate("themes") }
                    CrazeQuickAction(
                        R.string.craze_dashboard_typing_title,
                        R.string.craze_dashboard_typing_subtitle,
                        R.drawable.keyboard,
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate("keyboardAndTyping") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CrazeQuickAction(
                        R.string.craze_dashboard_size_title,
                        R.string.craze_dashboard_size_subtitle,
                        R.drawable.keyboard,
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate("resize") }
                    CrazeQuickAction(
                        R.string.craze_dashboard_languages_title,
                        R.string.craze_dashboard_languages_subtitle,
                        R.drawable.globe,
                        modifier = Modifier.weight(1f)
                    ) { navController.navigate("languages") }
                }
            }

            Spacer(Modifier.height(16.dp))
            HomeScreenLite.render(showTitle = false)

            if (isPaid || LocalInspectionMode.current) {
                Text(
                    stringResource(R.string.payment_paid_version_indicator),
                    style = Typography.SmallMl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
            Text(
                stringResource(R.string.craze_modified_notice),
                style = Typography.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                textAlign = TextAlign.Center
            )
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = Typography.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
        TextButton(
            onClick = {
                val intent = Intent().apply {
                    setClass(context, TextEditPopupActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.settings_try_typing_here),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CrazeQuickAction(
    titleRes: Int,
    subtitleRes: Int,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            Text(stringResource(titleRes), style = Typography.Body.Medium)
            Text(
                stringResource(subtitleRes),
                style = Typography.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CrazeKeyboardPreview() {
    val keyColor = Color.White.copy(alpha = 0.12f)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Q", "W", "E", "R", "T", "Y").forEach { letter ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = keyColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(letter, color = Color(0xFFE7E4FF), style = Typography.Small)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = keyColor
            ) { }
            Surface(
                modifier = Modifier
                    .weight(4f)
                    .height(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFB9F45A)
            ) { }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = keyColor
            ) { }
        }
    }
}
