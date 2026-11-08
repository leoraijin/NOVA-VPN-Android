package com.nova.vpn.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.*
import org.json.JSONObject

/** In-memory interpretation of the acknowledged policy and its actual VPN exits. */
class ActiveRoutePreview private constructor(
    private val policy: MultiServerPolicy,
    private val ipv6Enabled: Boolean,
    private val fakeIpRanges: List<FakeIpNetworkPreflight.Prefix>,
    private val uidGuard: SharedUidExitGuard?
) {
    internal data class Result(val text: String, val profileId: String? = null)

    internal fun explain(destination: String, app: String, uidPackages: List<String> = listOf(app.trim())): Result {
        val input = destination.trim()
        if (input.isEmpty()) return Result("Введите домен или IP-адрес.")
        val numeric = runCatching {
            FakeIpNetworkPreflight.Prefix(input + if (':' in input) "/128" else "/32")
        }.getOrNull()
        if (numeric != null && fakeIpRanges.any { it.overlaps(numeric) })
            return Result("Это адрес FakeIP. Введите исходный домен назначения.")
        if (numeric?.ipv6 == true && !ipv6Enabled) return Result("Блокируется · IPv6 отключён.")
        val domain = if (numeric != null) null else {
            SiteDomain.normalize(input)?.takeUnless { it.all { ch -> ch.isDigit() || ch == '.' } }
                ?: return Result("Введите домен без URL или корректный IP-адрес.")
        }
        if (uidGuard?.conflicts(uidPackages) == true)
            return Result("Блокируется · у приложений с общим UID разные назначения.")
        val packageName = app.trim().takeIf { it.isNotEmpty() && it in uidPackages }
        val decision = policy.decide(packageName, domain, ownerKnown = packageName != null)
        val source = when (decision.reason) {
            "site" -> "правило сайта"
            "application" -> "правило приложения"
            "default" -> "основной сервер"
            "missing-profile" -> "назначенный сервер недоступен"
            "unknown-owner" -> "приложение неизвестно"
            else -> "правило маршрутизации"
        }
        return when (decision.effective.kind) {
            ServerRouteKind.PROFILE -> Result("Через VPN · $source", decision.effective.profileId)
            ServerRouteKind.DIRECT -> Result("Напрямую · $source")
            ServerRouteKind.BLOCK -> Result("Блокируется · $source")
            ServerRouteKind.DEFAULT -> error("Default must resolve to a concrete decision")
        }
    }

    internal companion object {
        fun capture(settings: AppRoutingSettings, identity: RoutePolicyIdentity, exits: RouteExitBindings, config: String): ActiveRoutePreview? {
            val multi = settings.multiServer ?: return null
            if (identity != RoutePolicyIdentity.capture(settings)) return null
            val servers = JSONObject(config).getJSONObject("dns").getJSONArray("servers")
            val ranges = (0 until servers.length()).map { servers.getJSONObject(it) }
                .filter { it.optString("type") == "fakeip" }
                .flatMap { server -> listOf("inet4_range", "inet6_range").mapNotNull { key ->
                    server.optString(key).takeIf(String::isNotEmpty)?.let { FakeIpNetworkPreflight.Prefix(it) }
                } }
            return ActiveRoutePreview(MultiServerPolicy(multi.revision, multi.defaultProfileId, multi.rules, exits.entries.keys),
                settings.ipv6Enabled, ranges, SharedUidExitGuard.capture(config))
        }
    }
}

@Composable
internal fun ActiveRoutePreviewDialog(
    preview: ActiveRoutePreview,
    profiles: List<ImportedProfile>,
    apps: List<AppListEntry>,
    onDismiss: () -> Unit
) {
    var destination by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var chooseApp by remember { mutableStateOf(false) }
    val selectedApp = apps.firstOrNull { it.packageName == selectedPackage }
    val packageName = selectedApp?.packageName
    val packageManager = LocalContext.current.packageManager
    var requestedDestination by remember(packageName) { mutableStateOf<String?>(null) }
    var result by remember(packageName) { mutableStateOf<ActiveRoutePreview.Result?>(null) }
    var ownerUnavailable by remember(packageName) { mutableStateOf(false) }
    LaunchedEffect(requestedDestination, packageName, packageManager) {
        val requested = requestedDestination ?: return@LaunchedEffect
        val uidPackages = if (packageName == null) emptyList() else withContext(Dispatchers.IO) {
            runCatching {
                SharedUidRouting.groupFor(packageName,
                    { name -> runCatching { packageManager.getApplicationInfo(name, 0).uid }.getOrNull() },
                    { uid -> packageManager.getPackagesForUid(uid)?.toSet().orEmpty() }).toList()
            }.getOrDefault(emptyList())
        }
        if (requestedDestination == requested && destination == requested) {
            ownerUnavailable = packageName != null && uidPackages.isEmpty()
            result = preview.explain(requested, packageName.orEmpty(), uidPackages)
            requestedDestination = null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проверить маршрут") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Расчёт по правилам действующего подключения. Для домена результат зависит от его распознавания ядром.")
                OutlinedTextField(destination, {
                    destination = it
                    requestedDestination = null
                    result = null
                    ownerUnavailable = false
                }, label = { Text("Домен или IP-адрес") }, singleLine = true)
                OutlinedButton(onClick = { chooseApp = true }) {
                    Text(selectedApp?.label ?: "Выбрать приложение")
                }
                Text(packageName ?: "Сейчас моделируется соединение с неизвестным приложением.")
                if (ownerUnavailable)
                    Text("Android не подтвердил состав группы. Расчёт использует неизвестного владельца.")
                result?.let { decision ->
                    val name = decision.profileId?.let { id -> profiles.firstOrNull { it.id == id }?.displayName ?: id }
                    Text(listOfNotNull(decision.text, name).joinToString(" · "))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = requestedDestination == null, onClick = {
                result = null
                ownerUnavailable = false
                requestedDestination = destination
            }) { Text("Проверить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
    if (chooseApp) {
        var query by remember { mutableStateOf("") }
        val matches = remember(apps, query) { filterAppList(apps, query).distinctBy { it.packageName } }
        AlertDialog(
            onDismissRequest = { chooseApp = false },
            title = { Text("Выберите приложение") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Название или пакет") }, singleLine = true)
                    TextButton(onClick = { selectedPackage = null; chooseApp = false }) { Text("Неизвестное приложение") }
                    if (matches.isEmpty()) Text(if (apps.isEmpty()) "Список приложений недоступен." else "Приложения не найдены.")
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(matches, key = { it.packageName }) { app ->
                            TextButton(onClick = { selectedPackage = app.packageName; chooseApp = false }) {
                                Column { Text(app.label); Text(app.packageName) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseApp = false }) { Text("Закрыть") } }
        )
    }
}
