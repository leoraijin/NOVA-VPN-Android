package com.nova.vpn.android

import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nova.vpn.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun IdentityDialog(onClose:()->Unit) {
    val context=LocalContext.current
    val vault=remember { IdentityVault(context) }
    val scope=rememberCoroutineScope()
    var state by remember { mutableStateOf(IdentityState()) }
    var selected by remember { mutableStateOf<SubscriptionIdentity?>(null) }
    var name by remember { mutableStateOf("") };var hwid by remember { mutableStateOf("") }
    var header by remember { mutableStateOf("X-HWID") };var ua by remember { mutableStateOf("NOVA-VPN/1.0") }
    var extras by remember { mutableStateOf("") };var urls by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") };var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) };var loaded by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var fullIncoming by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var incoming by remember { mutableStateOf<IdentityState?>(null) }
    fun choose(p:SubscriptionIdentity?) { selected=p;name=p?.name.orEmpty();hwid=p?.hwid.orEmpty();header=p?.hwidHeader?:"X-HWID";ua=p?.userAgent?:"NOVA-VPN/1.0";extras=p?.headers?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty();urls=state.bindings.filterValues { it==p?.id }.keys.joinToString("\n") }
    fun edited():IdentityState {
        val p=selected?:error("Сначала создайте профиль.")
        val h=linkedMapOf<String,String>();extras.lineSequence().filter { it.isNotBlank() }.forEach { val n=it.indexOf(':');require(n>0) { "Заголовок: Имя: значение." };val k=it.substring(0,n).trim();require(h.keys.none { old->old.equals(k,true) }) { "Повторяющийся заголовок." };h[k]=it.substring(n+1).trim() }
        val next=p.copy(name=name.trim(),hwid=hwid.trim(),hwidHeader=header.trim(),userAgent=ua.trim(),headers=h)
        val bindings=state.bindings.filterValues { it!=p.id }+urls.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.associateWith { p.id }
        return IdentityState(state.identities.filterNot { it.id==p.id }+next,bindings,state.platforms).also { it.validate() }
    }
    fun work(action:suspend ()->Unit) { busy=true;scope.launch { try {action()} catch(e:Exception) {message=e.message?:"Не удалось выполнить операцию."} finally {busy=false} } }
    LaunchedEffect(Unit) { try {state=withContext(Dispatchers.IO){vault.load()};choose(state.identities.firstOrNull());loaded=true} catch(e:Exception){message="Не удалось открыть хранилище. Изменения заблокированы."} }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null)work {code=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)!!.use {stream->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=12*1024*1024){"Файл слишком большой."};output.write(buffer,0,n)};String(output.toByteArray(),Charsets.UTF_8)}}} }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {uri->if(uri!=null)work{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)!!.use {it.write(code.toByteArray(Charsets.UTF_8))}};message="Зашифрованный код сохранён."} }
    val saveQr=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {uri->if(uri!=null)work{val image=qr?:error("Сначала создайте QR");withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)!!.use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}};message="QR сохранён."} }
    Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Идентификация подписок",style=MaterialTheme.typography.headlineSmall)
                Text("Параметры сохраняются локально и пока не отправляются провайдерам. Передавайте пароль отдельно от кода.")
                state.identities.forEach { p->OutlinedButton(enabled=!busy,onClick={choose(p)},modifier=Modifier.fillMaxWidth()){Text((if(selected?.id==p.id) "✓ " else "")+p.name)} }
                Button(enabled=loaded&&!busy,onClick={val p=SubscriptionIdentity();state=state.copy(identities=state.identities+p);choose(p)}){Text("Создать идентичность")}
                if(selected!=null) {
                    OutlinedTextField(name,{name=it},label={Text("Название")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    OutlinedTextField(hwid,{hwid=it},label={Text("Виртуальный HWID")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    OutlinedTextField(header,{header=it},label={Text("Имя заголовка HWID")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    OutlinedTextField(ua,{ua=it},label={Text("User-Agent")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    Text("Шаблоны User-Agent")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {OutlinedButton(enabled=!busy,onClick={ua="NOVA-VPN-Android"},modifier=Modifier.weight(1f)){Text("NOVA Android")};OutlinedButton(enabled=!busy,onClick={ua="NOVA-VPN/2.0"},modifier=Modifier.weight(1f)){Text("NOVA Windows")}}
                    OutlinedTextField(extras,{extras=it},label={Text("Заголовки: Имя: значение")},minLines=2,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    OutlinedTextField(urls,{urls=it},label={Text("HTTPS-подписки, по одной на строку")},minLines=2,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                    Button(enabled=!busy&&loaded,onClick={work{val next=edited();withContext(Dispatchers.IO){vault.save(next)};state=next;message="Профиль сохранён."}}){Text("Сохранить профиль")}
                    OutlinedButton(enabled=!busy&&loaded,onClick={work{val id=selected!!.id;val next=state.copy(identities=state.identities.filterNot {it.id==id},bindings=state.bindings.filterValues {it!=id});withContext(Dispatchers.IO){vault.save(next)};state=next;choose(next.identities.firstOrNull())}}){Text("Удалить профиль")}
                }
                OutlinedTextField(password,{password=it},label={Text("Пароль переноса: 10–256 символов")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=!busy)
                OutlinedTextField(code,{code=it},label={Text("Код переноса")},minLines=3,maxLines=5,modifier=Modifier.fillMaxWidth(),enabled=!busy)
                Button(enabled=!busy&&selected!=null,onClick={work{val next=edited();val profile=next.identities.first {it.id==selected!!.id};code=withContext(Dispatchers.Default){IdentityTransfer.export(IdentityState(listOf(profile)),password)};message="Код содержит выбранную идентичность без ссылок подписок."}}){Text("Экспорт выбранного")}
                Button(enabled=!busy&&loaded,onClick={work{incoming=withContext(Dispatchers.Default){IdentityTransfer.import(code,password)}}}){Text("Импорт кода")}
                OutlinedButton(enabled=!busy,onClick={open.launch(arrayOf("*/*"))}){Text("Открыть файл")}
                OutlinedButton(enabled=!busy&&code.startsWith("NOVA-ID1:"),onClick={save.launch("identity.novaid")}){Text("Сохранить код в файл")}
                OutlinedButton(enabled=!busy&&code.startsWith("NOVA-ID1:"),onClick={work{qr=withContext(Dispatchers.Default){IdentityQr.bitmap(code)}}}){Text("Показать QR")}
                OutlinedButton(enabled=!busy,onClick={
                    val options=com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder().setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
                    com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context,options).startScan().addOnSuccessListener {result->val text=result.rawValue.orEmpty();if(text.startsWith("NOVA-ID1:") && text.length<=4096){code=text;message="QR прочитан. Введите пароль и нажмите «Импорт кода»."}else message="Это не QR идентичности NOVA."}.addOnFailureListener {message="Не удалось открыть сканер QR. Используйте код или файл."}
                }){Text("Сканировать QR")}
                qr?.let { Image(it.asImageBitmap(),contentDescription="Зашифрованный профиль идентичности",modifier=Modifier.fillMaxWidth().aspectRatio(1f));OutlinedButton(enabled=!busy,onClick={saveQr.launch("identity-qr.png")}){Text("Сохранить QR в PNG")} }
                Button(enabled=!busy&&loaded,onClick={work{code=withContext(Dispatchers.IO){FullTransfer.export(context,password)};message="Полный пакет зашифрован. Сохраните его в файл; большой пакет не помещается в QR."}}){Text("Полный перенос: экспорт")}
                OutlinedButton(enabled=!busy&&loaded,onClick={work{check(!VpnRuntime.state.value.connected&&!VpnRuntime.state.value.connecting){"Полное восстановление доступно при отключённом VPN. Соединение не изменено."};fullIncoming=withContext(Dispatchers.Default){FullTransfer.read(code,password)}}}){Text("Полный перенос: восстановить")}
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(message)
                TextButton(enabled=!busy,onClick=onClose){Text("Закрыть")}
            }
        }
    }
    fullIncoming?.let {doc->AlertDialog(onDismissRequest={if(!busy)fullIncoming=null},title={Text("Полное восстановление")},text={Text("Заменить профили и настройки? Профилей: ${doc.getJSONArray("profiles").length()}. Настройки другой ОС сохранятся для обратного переноса; её пути и приложения не применяются. Автоподключение будет выключено.")},confirmButton={TextButton(enabled=!busy,onClick={work{check(!VpnRuntime.state.value.connected&&!VpnRuntime.state.value.connecting){"Соединение активно; импорт отменён."};withContext(Dispatchers.IO){FullTransfer.restore(context,doc)};fullIncoming=null;onClose();(context as? android.app.Activity)?.recreate()}}){Text("Восстановить")}},dismissButton={TextButton(enabled=!busy,onClick={fullIncoming=null}){Text("Отмена")}})}
    incoming?.let { imported -> AlertDialog(onDismissRequest={incoming=null},title={Text("Импорт идентичностей")},text={Text("Добавить: ${imported.identities.joinToString { it.name }}? Совпадающие ID будут заменены.")},confirmButton={TextButton(onClick={work{val ids=imported.identities.map {it.id}.toSet();val next=IdentityState(state.identities.filterNot {it.id in ids}+imported.identities,state.bindings+imported.bindings,state.platforms);next.validate();withContext(Dispatchers.IO){vault.save(next)};state=next;choose(imported.identities.firstOrNull());incoming=null;message="Импорт сохранён."}}){Text("Импортировать")}},dismissButton={TextButton(onClick={incoming=null}){Text("Отмена")}}) }
}
