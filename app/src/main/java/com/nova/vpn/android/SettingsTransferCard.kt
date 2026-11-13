package com.nova.vpn.android

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nova.vpn.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun SettingsTransferCard() {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportKeys by remember { mutableStateOf<Boolean?>(null) }
    var password by remember { mutableStateOf("") }
    var encryptedInput by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<RoutingSharePackage?>(null) }
    var available by remember { mutableStateOf<List<ImportedProfile>>(emptyList()) }
    val mapping=remember { mutableStateMapOf<String,String>() }
    var pendingSave by remember { mutableStateOf<String?>(null) }
    fun work(action: suspend () -> Unit) {
        busy=true;message=null
        scope.launch {
            try { action() } catch(error: Exception) {
                if(error is kotlinx.coroutines.CancellationException) throw error
                message=if(error is org.json.JSONException) "Файл повреждён или имеет неподдерживаемый формат."
                    else error.message ?: "Не удалось выполнить перенос."
            } finally { busy=false }
        }
    }
    suspend fun preview(text: String, secret: String) {
        val parsed=withContext(Dispatchers.Default) { RoutingShare.read(text,secret) }
        val local=withContext(Dispatchers.IO) { ProfileVault(context).load().filterNot { it.retiredFromSubscription } }
        mapping.clear()
        parsed.references.forEach { id -> if(local.any { it.id==id }) mapping[id]=id }
        available=local;incoming=parsed;encryptedInput=null;password=""
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) work {
            val text=withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val n=stream.read(buffer);if(n<0)break
                        require(output.size()+n<=12*1024*1024) { "Файл слишком большой." };output.write(buffer,0,n) }
                    output.toString("UTF-8")
                } ?: error("Не удалось прочитать файл.")
            }
            if(text.trimStart().startsWith("NOVA-ID1:")) { encryptedInput=text;password="" } else preview(text,"")
        }
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val content=pendingSave;pendingSave=null
        if(uri!=null && content!=null) work {
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("Не удалось записать файл.") }
            message="Файл сохранён. На другом телефоне откройте Настройки → Поделиться и перенести → Импорт из файла."
        }
    }
    fun export(keys: Boolean, share: Boolean) = work {
        val secret=password
        val content=withContext(Dispatchers.IO) { RoutingShare.export(context,keys,secret) }
        password="";exportKeys=null
        val filename=if(keys) "NOVA-keys-and-routing.nova" else "NOVA-routing.json"
        if(share) {
            val file=withContext(Dispatchers.IO) {
                val folder=File(context.cacheDir,"routing-shares").apply { mkdirs() }
                folder.listFiles()?.filter { it.isFile && System.currentTimeMillis()-it.lastModified()>24*60*60*1000L }?.forEach { it.delete() }
                File(folder,"${UUID.randomUUID()}-$filename").also { it.writeText(content,Charsets.UTF_8) }
            }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.routing-shares",file)
            val intent=Intent(Intent.ACTION_SEND).setType(if(keys) "application/octet-stream" else "application/json")
                .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData=ClipData.newUri(context.contentResolver,"NOVA VPN",uri)
            context.startActivity(Intent.createChooser(intent,"Поделиться настройками NOVA"))
        } else { pendingSave=content;save.launch(filename) }
    }
    SoftCard {
        Text("Поделиться и перенести", style=MaterialTheme.typography.titleMedium)
        Text("Все профили маршрутизации: сайты, приложения и назначения VPN-1/VPN-2.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={password="";exportKeys=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Ключи + маршрутизация") }
        OutlinedButton(onClick={password="";exportKeys=false},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Только маршрутизация") }
        TextButton(onClick={open.launch(arrayOf("*/*"))},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Импорт из файла") }
        if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        message?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
    }
    exportKeys?.let { keys ->
        AlertDialog(onDismissRequest={if(!busy){exportKeys=null;password=""}},
            title={Text(if(keys) "Ключи + маршрутизация" else "Только маршрутизация")},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(keys) "Все доступные ключи и профили маршрутизации. Ссылки подписок и оформление не передаются. Передайте пароль получателю отдельно."
                    else "Правила сайтов и приложений без ключей и ссылок подписок. На другом телефоне можно выбрать серверы для назначений.")
                if(keys) OutlinedTextField(value=password,onValueChange={password=it},label={Text("Пароль от 10 символов")},
                    visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy)
                message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }},confirmButton={TextButton(enabled=!busy&&(!keys||password.length in 10..256),onClick={export(keys,true)}){Text("Поделиться")}},
            dismissButton={TextButton(enabled=!busy&&(!keys||password.length in 10..256),onClick={export(keys,false)}){Text("Сохранить файл")}})
    }
    encryptedInput?.let { code ->
        AlertDialog(onDismissRequest={if(!busy){encryptedInput=null;password=""}},title={Text("Пароль пакета")},
            text={Column { OutlinedTextField(value=password,onValueChange={password=it},label={Text("Пароль")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy)
                message?.let { Text(it,color=MaterialTheme.colorScheme.error) } }},
            confirmButton={TextButton(enabled=!busy&&password.length in 10..256,onClick={work{preview(code,password)}}){Text("Открыть")}},
            dismissButton={TextButton(enabled=!busy,onClick={encryptedInput=null;password=""}){Text("Отмена")}})
    }
    incoming?.let { pack ->
        val embedded=pack.profiles.map { it.id }.toSet()
        val needed=pack.references-embedded
        AlertDialog(onDismissRequest={if(!busy)incoming=null},title={Text("Импорт настроек")},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Ключей: ${pack.profiles.size}. Профилей маршрутизации: ${pack.routing.presets.size}.")
                Text("Прежние ключи и профили сохранятся. Полученные маршруты добавятся отдельными профилями, и выбранный отправителем профиль станет активным. Оформление не изменится. Импорт выполняется при отключённом VPN.")
                needed.forEach { id ->
                    var expanded by remember(id) { mutableStateOf(false) }
                    Column {
                        Text(pack.labels[id]?:"Сервер",style=MaterialTheme.typography.labelLarge)
                        OutlinedButton(onClick={expanded=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(available.firstOrNull { it.id==mapping[id] }?.displayName?:"Выбрать местный сервер") }
                        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=260.dp)) {
                            available.forEach { profile -> DropdownMenuItem(text={Text(profile.displayName)},onClick={mapping[id]=profile.id;expanded=false}) }
                        }
                    }
                }
                if(needed.isNotEmpty()&&available.isEmpty()) Text("Сначала добавьте ключи на этот телефон.")
                message?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }},confirmButton={TextButton(enabled=!busy&&needed.all { mapping[it]!=null },onClick={work {
                val choices=mapping.toMap()
                withContext(Dispatchers.IO) { FullTransfer.restoreShared(context,pack,choices) }
                incoming=null;message="Импорт завершён. Прежние профили сохранены; подключение можно включить вручную."
            }}){Text("Импортировать")}},dismissButton={TextButton(enabled=!busy,onClick={incoming=null}){Text("Отмена")}})
    }
}
