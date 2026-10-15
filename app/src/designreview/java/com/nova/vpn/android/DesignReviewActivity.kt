package com.nova.vpn.android

import android.os.Bundle
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.*

/** Isolated visual fixture: never attaches VpnRuntime, starts services or saves real profiles. */
class DesignReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val style = DesignStyle.entries.firstOrNull { it.name == intent.getStringExtra("style") } ?: DesignStyle.IOS_LIQUID_GLASS
        val mode = if (intent.getStringExtra("mode") == "DARK") ThemeMode.DARK else ThemeMode.LIGHT
        val initialPage = intent.getStringExtra("page") ?: "Home"
        val state = intent.getStringExtra("state") ?: "idle"
        setContent {
            var appearance by remember { mutableStateOf(Appearance(style, mode, intent.getFloatExtra("hue", 275f), intent.getFloatExtra("frost", .65f))) }
            var page by remember { mutableStateOf(initialPage) }
            val colors = designColorScheme(appearance.style, this, appearance.isDark, appearance.materialHue)
            val profile = remember { ImportedProfile(id = "preview-nl", name = "Netherlands · Amsterdam", protocol = "vless", server = "preview.invalid", port = 443, outboundJson = "{}") }
            val profiles = remember { listOf(profile, profile.copy(id = "preview-fi", name = "Finland · Helsinki")) }
            var selectedProfileId by remember { mutableStateOf(profile.id) }
            val vpn = when (state) {
                "connected" -> VpnUiState(connected = true, trafficVerified = true)
                "loading" -> VpnUiState(connecting = true)
                "warning" -> VpnUiState(connected = true, warning = "Контрольный запрос не ответил", trafficCheckFailed = true)
                else -> VpnUiState()
            }
            val reviewFont = intent.getFloatExtra("font", LocalConfiguration.current.fontScale)
            val reviewConfiguration = Configuration(LocalConfiguration.current).apply { fontScale = reviewFont }
            CompositionLocalProvider(LocalGlassTierOverride provides intent.getIntExtra("glassTier", 2),
                LocalConfiguration provides reviewConfiguration,
                LocalDensity provides Density(LocalDensity.current.density, reviewFont)) {
            NovaTheme(appearance, colors) {
                Scaffold(containerColor = colors.background, bottomBar = {
                    NovaNavigationBar {
                        listOf(Triple("Home", "Главная", Icons.Rounded.Home), Triple("Servers", "Серверы", Icons.Rounded.Shield),
                            Triple("Routing", "Маршрутизация", Icons.Rounded.Router), Triple("Settings", "Настройки", Icons.Rounded.Settings)).forEach { (id, label, icon) ->
                            NovaNavigationItem(page == id, true, { page = id }, icon, label)
                        }
                    }
                }) { padding ->
                    Box(Modifier.fillMaxSize().novaBackdropSource().padding(
                        if (page == "GlassProbe") PaddingValues(0.dp) else PaddingValues(top = padding.calculateTopPadding(), bottom =
                            if (appearance.style == DesignStyle.IOS_LIQUID_GLASS) 0.dp else padding.calculateBottomPadding()))) {
                        when (page) {
                            "GlassProbe" -> Canvas(Modifier.fillMaxSize()) {
                                val phase = intent.getIntExtra("pattern", 0)
                                val stripe = 24.dp.toPx()
                                var x = 0f
                                var i = 0
                                while (x < size.width) {
                                    drawRect(if ((i + phase) % 2 == 0) Color(0xFFFF481C) else Color(0xFF00D7F5),
                                        topLeft = Offset(x, 0f), size = Size(stripe, size.height))
                                    x += stripe; i++
                                }
                            }
                            "Tutorial" -> BoxWithConstraints(Modifier.fillMaxSize()) {
                                val available = maxHeight
                                var index by remember { mutableIntStateOf(intent.getIntExtra("step", 0)) }
                                val step = TutorialStep.at(index) ?: TutorialStep.Import
                                Column(Modifier.fillMaxSize()) {
                                    TutorialPanel(step, false, { index = (index - 1).coerceAtLeast(0) },
                                        { index = (index + 1) % 6 }, { page = "Home" }, available)
                                    Box(Modifier.weight(1f)) {
                                        CompositionLocalProvider(LocalTutorialStep provides step) {
                                            when (step) {
                                                TutorialStep.Import, TutorialStep.Servers -> ServersPage(profiles, selectedProfileId, vpn, ServerCheckState(), {}, {}, {}, null, null, null, false, {}, { selectedProfileId = it }, {}, {})
                                                TutorialStep.Apps, TutorialStep.Sites -> RoutingPage(emptyList(), false, false, AppRoutingSettings(), {})
                                                TutorialStep.Updates -> SettingsPage({}, {}, false, appearance, true, false, null, null, {}, {}, { appearance = it }, {}, {}, {})
                                                TutorialStep.Connect -> HomePage(profiles, profile.id, profile, vpn, null, {}, {}, {})
                                            }
                                        }
                                    }
                                }
                            }
                            "Home" -> HomePage(profiles, profile.id, profile, vpn, null, {}, {}, {})
                            "Servers" -> ServersPage(profiles, selectedProfileId, vpn, ServerCheckState(), {}, {}, {}, null, null, null, false, {}, { selectedProfileId = it }, {}, {})
                            "Routing" -> RoutingPage(emptyList(), false, false, AppRoutingSettings(), {})
                            "Settings" -> SettingsPage({}, {}, false, appearance, true, false, null, null, {}, {}, { appearance = it }, {}, {}, {})
                            "Appearance" -> PageFrame("Оформление", "Четыре стиля · два режима") { item { SoftCard { AppearancePicker(appearance) { appearance = it } } } }
                            "Components" -> PageFrame("Компоненты", "Состояния элементов управления") {
                                item { SoftCard {
                                    NovaButton({}) { Text("Подключить") }
                                    NovaButton({}, enabled = false) { Text("Недоступно") }
                                    NovaOutlinedButton({}) { Text("Добавить конфигурацию") }
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        NovaSwitch(true, {}); NovaSwitch(false, {}); NovaSwitch(true, {}, enabled = false)
                                    }
                                    OutlinedTextField("example.com", {}, label = { Text("Домен") }, shape = novaFieldShape())
                                    OutlinedTextField("неверный адрес", {}, isError = true, label = { Text("Домен") }, shape = novaFieldShape())
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                } }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
