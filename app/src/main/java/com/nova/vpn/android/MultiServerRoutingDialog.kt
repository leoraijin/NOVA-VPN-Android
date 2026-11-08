package com.nova.vpn.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nova.vpn.android.data.*

/** Edits a private draft; only Apply publishes through the existing staged route transaction. */
@Composable
internal fun MultiServerRoutingDialog(settings: AppRoutingSettings, profiles: List<ImportedProfile>, apps: List<AppListEntry>,
    selectedProfileId: String?, busy: Boolean, onApply: (AppRoutingSettings) -> Unit, onDismiss: () -> Unit,resuming: Boolean=false,previousProfileId: String?=selectedProfileId) {
    var primary by remember { mutableStateOf(settings.multiServer?.defaultProfileId ?: (if(resuming)settings.history?.savedAssignments?.defaultProfileId else null) ?: selectedProfileId.orEmpty()) }
    var rules by remember { mutableStateOf(settings.multiServer?.rules ?: (if(resuming)settings.history?.savedAssignments?.rules else null) ?: (
        settings.packages.sorted().map { MultiServerRule(packageName=it,target=ServerRouteTarget(
            if(settings.mode==AppRoutingMode.EXCLUDE_SELECTED) ServerRouteKind.DIRECT else ServerRouteKind.DEFAULT)) } +
        settings.sites.map { MultiServerRule(domain=it.domain,includeSubdomains=true,target=ServerRouteTarget(
            if(it.target==SiteRoutingTarget.DIRECT) ServerRouteKind.DIRECT else ServerRouteKind.DEFAULT)) })) }
    var ipv6 by remember {mutableStateOf(settings.ipv6Enabled)}
    var tab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(emptySet<String>()) }
    var domain by remember { mutableStateOf("") }
    var subdomains by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf(false) }
    var choosePrimary by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<MultiServerRule?>(null) }
    var addingSite by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var migrationAccepted by remember { mutableStateOf(!RoutingModeTransitions.needsMigrationReview(settings,resuming)) }
    var compatibility by remember { mutableStateOf<Map<String,String?>>(emptyMap()) }
    var compatibilityLoading by remember { mutableStateOf(true) }
    LaunchedEffect(profiles) {
        compatibilityLoading=true
        compatibility=withContext(Dispatchers.Default) { profiles.associate { it.id to MultiServerProfileAdapter.problem(it) } }
        compatibilityLoading=false
    }
    val context=LocalContext.current
    var inventoryVersion by remember { mutableStateOf(0) }
    var appGroups by remember { mutableStateOf<List<RoutingAppGroup>>(emptyList()) }
    var groupsLoading by remember { mutableStateOf(true) }
    var groupsFailed by remember { mutableStateOf(false) }
    val assignedPackages=rules.mapNotNull { it.packageName }.toSet()
    DisposableEffect(context) {
        val receiver=object: android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?,intent: android.content.Intent?) { inventoryVersion++ }
        }
        val filter=android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(context,receiver,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(apps,assignedPackages,inventoryVersion) {
        groupsLoading=true;groupsFailed=false;selectedApps=emptySet()
        if(choosing && editRule==null && !choosePrimary) choosing=false
        try { appGroups=withContext(Dispatchers.IO) { loadRoutingAppGroups(context.packageManager,context.packageName,apps,assignedPackages) } }
        catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch(_:Exception) { groupsFailed=true;appGroups=emptyList() }
        finally { groupsLoading=false }
    }
    val migrating = RoutingModeTransitions.needsMigrationReview(settings,resuming)
    val editablePrimary = PreparedVpnConfiguration.primaryForRoutingEdit(profiles, settings, primary)
    val primaryAllowed = editablePrimary != null &&
        (editablePrimary.retiredFromSubscription || compatibility.containsKey(primary) && compatibility[primary] == null)
    fun label(target: ServerRouteTarget): String = when(target.kind) {
        ServerRouteKind.DEFAULT -> "Основной сервер"
        ServerRouteKind.DIRECT -> "Напрямую"
        ServerRouteKind.BLOCK -> "Блокировать"
        ServerRouteKind.PROFILE -> profiles.firstOrNull { it.id==target.profileId }?.let { it.displayName+(if(it.retiredFromSubscription)" · удалён из подписки — трафик блокируется" else "") } ?: "Сервер недоступен — трафик блокируется"
    }
    fun closePicker() {
        choosing = false
        editRule = null
        addingSite = false
    }
    fun choose(target: ServerRouteTarget) {
        val editing = editRule
        when {
            choosePrimary -> primary = requireNotNull(target.profileId)
            editing != null && addingSite -> {
                rules = rules + editing.copy(target = target)
                domain = ""
                error = null
            }
            editing != null -> rules = rules.map { if (it == editing) it.copy(target = target) else it }
            else -> rules = assignRoutingApps(rules, selectedApps, target)
        }
        closePicker()
        selectedApps = emptySet()
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    item(key = "routing-editor-header") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Серверы для приложений и сайтов",style=MaterialTheme.typography.titleLarge)
                            Text("Правило сайта важнее правила приложения. Остальной трафик — через основной сервер.")
                            OutlinedButton(enabled=!busy,onClick={choosePrimary=true;choosing=true}) {
                                val label = profiles.firstOrNull { it.id == primary }?.let {
                                    it.displayName + if (it.retiredFromSubscription) " · удалён из подписки" else ""
                                } ?: if (editablePrimary != null) "Сервер недоступен" else "Выберите сервер"
                                Text("Основной: $label")
                            }
                            if (editablePrimary?.retiredFromSubscription == true) {
                                Text("Трафик основного маршрута блокируется. Другие назначения продолжают действовать.")
                            }
                            Row {Switch(ipv6,{ipv6=it},enabled=!busy);Text("IPv6 через назначенные серверы")}
                            if(!ipv6)Text("IPv6 будет блокироваться внутри VPN, чтобы не уходить в обход назначений.")
                            if(migrating) {
                                Text("Будет включена новая схема. Остальные приложения пойдут через основной сервер. В новом режиме применяются назначения ниже. Прежние правила сохраняются для возврата; проверьте переход.")
                                Row { Checkbox(migrationAccepted,onCheckedChange={migrationAccepted=it});Text("Я проверил изменение маршрутов") }
                            }
                            Row { FilterChip(tab==0,{tab=0},label={Text("Приложения")});Spacer(Modifier.width(8.dp));FilterChip(tab==1,{tab=1},label={Text("Сайты")}) }
                            OutlinedTextField(search,{search=it},label={Text("Поиск")},singleLine=true,modifier=Modifier.fillMaxWidth())
                            if(tab==0) {
                                if(groupsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if(groupsFailed) Text("Не удалось загрузить группы приложений.",color=MaterialTheme.colorScheme.error)
                                TextButton(enabled=!busy&&!groupsLoading,onClick={inventoryVersion++}) { Text("Обновить приложения") }
                                TextButton(enabled=selectedApps.isNotEmpty()&&!busy&&!groupsLoading,onClick={choosePrimary=false;editRule=null;choosing=true}) {Text("Назначить сервер (${selectedApps.size})")}
                            } else {
                                OutlinedTextField(domain,{domain=it;error=null},label={Text("Домен, например chatgpt.com")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                Row { Checkbox(subdomains,{subdomains=it});Text("Включая поддомены") }
                                TextButton(enabled=!busy,onClick={
                                    val normalized=SiteDomain.normalize(domain)
                                    if(normalized==null || normalized.matches(Regex("[0-9.]+"))) error="Введите домен без https:// и пути страницы."
                                    else if(rules.any { it.domain==normalized }) error="Этот домен уже добавлен."
                                    else {
                                        editRule = MultiServerRule(domain = normalized, includeSubdomains = subdomains, target = ServerRouteTarget(ServerRouteKind.DEFAULT))
                                        addingSite = true
                                        choosePrimary = false
                                        choosing = true
                                    }
                                }) {Text("Добавить сайт")}
                            }
                        }
                    }
                    if(tab==0) {
                        items(appGroups.filter { filterAppList(it.members,search).isNotEmpty() },key={it.key}) { group ->
                            val assignments=group.packages.map { name -> rules.firstOrNull { it.packageName==name }?.target ?: ServerRouteTarget(ServerRouteKind.DEFAULT) }.distinct()
                            val available=group.unavailableReason==null&&!groupsLoading
                            SoftCard {
                                Row {
                                    Checkbox(group.packages.all { it in selectedApps },{checked->selectedApps=if(checked)selectedApps+group.packages else selectedApps-group.packages},enabled=available&&!busy)
                                    Column(Modifier.weight(1f)) {
                                        group.members.forEach { app ->
                                            Row {
                                                if(app.icon!=null) androidx.compose.ui.viewinterop.AndroidView(factory={ctx->android.widget.ImageView(ctx)},
                                                    update={it.setImageDrawable(app.icon)},modifier=Modifier.size(28.dp))
                                                Column { Text(app.label);Text(app.packageName,style=MaterialTheme.typography.bodySmall) }
                                            }
                                        }
                                    }
                                }
                                if(group.members.size>1) Text("Общий сетевой идентификатор: маршрут изменится для всей группы.",style=MaterialTheme.typography.bodySmall)
                                group.unavailableReason?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                                TextButton(enabled=available&&!busy,onClick={selectedApps=group.packages;choosePrimary=false;editRule=null;choosing=true}) {
                                    Text(if(assignments.size==1)label(assignments.single()) else "Разные маршруты — назначьте один для группы")
                                }
                                if(rules.any { it.packageName in group.packages }) TextButton(enabled=!busy&&!groupsLoading,onClick={rules=assignRoutingApps(rules,group.packages,null)}) {Text("Убрать назначение")}
                            }
                        }
                    } else items(rules.filter { it.domain!=null && it.domain.contains(search,true) },key={it.domain+":"+it.includeSubdomains}) { rule ->
                        SoftCard {
                            Text(rule.domain.orEmpty());Text(if(rule.includeSubdomains)"Домен и поддомены" else "Только этот домен")
                            TextButton(enabled=!busy,onClick={choosePrimary=false;editRule=rule;choosing=true}) {Text(label(rule.target))}
                            TextButton(enabled=!busy,onClick={rules=rules-rule}) {Text("Удалить правило")}
                        }
                    }
                }
                error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                Row {
                    TextButton(onClick=onDismiss,enabled=!busy){Text("Отмена")}
                    Button(enabled=!busy&&!compatibilityLoading&&!groupsLoading&&!groupsFailed&&primaryAllowed&&migrationAccepted,onClick={
                        try {
                            onApply(RoutingModeTransitions.enable(settings,previousProfileId,primary,rules,ipv6))
                            onDismiss()
                        }catch(_:IllegalArgumentException){error="Проверьте назначения серверов и домены."}
                    }){Text("Применить")}
                }
            }
        }
    }
    if(choosing) {
        var serverSearch by remember {mutableStateOf("")}
        val visibleProfiles = remember(profiles, serverSearch) {
            ServerListFilter.apply(profiles, serverSearch, favoritesOnly = false, sort = ServerSort.MANUAL, country = ServerMapData::flagCountry)
        }
        AlertDialog(onDismissRequest=::closePicker,title={Text(if (choosePrimary) "Выберите основной сервер" else "Выберите назначение")},text={
            LazyColumn(Modifier.heightIn(max=380.dp)) {
                item(key = "server-picker-header") {
                    Column {
                        if (!choosePrimary) {
                            val host = editRule?.domain
                            if (host != null) Text(host) else Text("Назначение для ${selectedApps.size} приложений, включая связанные в группы.")
                        }
                        OutlinedTextField(serverSearch,{serverSearch=it},label={Text("Название, страна или транспорт")},singleLine=true)
                    }
                }
                if(!choosePrimary) {
                    item {TextButton(onClick={choose(ServerRouteTarget(ServerRouteKind.DEFAULT))}){Text("Основной сервер")}}
                    item {TextButton(onClick={choose(ServerRouteTarget(ServerRouteKind.DIRECT))}){Text("Напрямую")}}
                    item {TextButton(onClick={choose(ServerRouteTarget(ServerRouteKind.BLOCK))}){Text("Блокировать")}}
                }
                items(visibleProfiles,key={it.id}) { profile ->
                    TextButton(enabled=!compatibilityLoading&&compatibility.containsKey(profile.id)&&compatibility[profile.id]==null,onClick={choose(ServerRouteTarget(ServerRouteKind.PROFILE,profile.id))}) {
                        Text(profile.displayName+" · "+profile.protocol+" · "+profileTransport(profile)+(if(profile.retiredFromSubscription)" · Удалён из подписки" else compatibility[profile.id]?.let { " · "+it }.orEmpty()))
                    }
                }
            }
        },confirmButton={TextButton(onClick=::closePicker){Text("Закрыть")}})
    }
}
