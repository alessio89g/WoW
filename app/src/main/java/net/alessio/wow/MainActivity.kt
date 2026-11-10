@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package net.alessio.wow

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val store by lazy { ProfileStore(this) }
    private var showDeviceKeys by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val preferences = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            var italian by remember { mutableStateOf(preferences.getBoolean("italian", false)) }
            val systemConfiguration = LocalConfiguration.current
            val strings = remember(italian, systemConfiguration) {
                val configuration = Configuration(systemConfiguration).apply {
                    setLocale(if (italian) Locale.ITALIAN else Locale.ENGLISH)
                }
                createConfigurationContext(configuration).resources
            }
            CompositionLocalProvider(LocalWoWResources provides strings) {
                MaterialExpressiveTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme() else expressiveLightColorScheme()) {
                    UpdatePrompts(this@MainActivity) { checkUpdates ->
                        AppMenu(
                            italian = italian,
                            setItalian = { selected ->
                                italian = selected
                                preferences.edit().putBoolean("italian", selected).apply()
                            },
                            checkUpdates = checkUpdates
                        ) { openMenu -> Home(openMenu) }
                    }
                    if(showDeviceKeys) DeviceKeyDialog { showDeviceKeys=false }
                }
            }
        }
    }
    @Composable private fun Home(openMenu: () -> Unit) {
        val initial=remember { runCatching { store.read() } }
        var targets by remember { mutableStateOf(initial.getOrDefault(emptyList())) }
        var editing by remember { mutableStateOf<Target?>(null) }
        var adding by rememberSaveable { mutableStateOf(false) }
        var deleting by remember { mutableStateOf<Target?>(null) }
        var sending by remember { mutableStateOf<String?>(null) }
        var keyRequest by remember { mutableStateOf<Pair<Target,RouterKeyRequired>?>(null) }
        val strings by rememberUpdatedState(LocalWoWResources.current)
        val snack=remember { SnackbarHostState() }; val scope=rememberCoroutineScope()
        LaunchedEffect(strings) { snack.currentSnackbarData?.dismiss() }
        if(initial.isFailure) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                    MenuButton(openMenu)
                    Text(label(R.string.unreadable_profiles),style=MaterialTheme.typography.headlineMedium)
                    Text(label(R.string.unreadable_profiles_detail))
                }
            }
            return
        }
        Scaffold(snackbarHost={SnackbarHost(snack)},floatingActionButton={
            ExtendedFloatingActionButton(onClick={adding=true}) { Text(label(R.string.add_pc)) }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=24.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        MenuButton(openMenu)
                        Text("WoW",style=MaterialTheme.typography.displayLarge)
                    }
                    Text(label(R.string.tagline),style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={showDeviceKeys=true}) { Text(label(R.string.device_ssh_key)) }
                }
                if(targets.isEmpty()) item {
                    Column(Modifier.padding(vertical=32.dp)) {
                        Text(label(R.string.ready_to_wake),style=MaterialTheme.typography.headlineMedium)
                        Text(label(R.string.empty_profiles),Modifier.padding(top=12.dp))
                    }
                }
                items(targets,key={it.id}) { t ->
                    Card(shape=RoundedCornerShape(28.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(t.name,style=MaterialTheme.typography.headlineSmall)
                            Text("${t.host} · ${if(t.isSsh) "SSH" else "UDP"} ${t.port}",style=MaterialTheme.typography.bodyLarge)
                            Text(t.mac,style=MaterialTheme.typography.labelLarge)
                            if(t.isSsh) Text(if(t.usesDeviceKey) label(R.string.auth_device) else label(R.string.auth_password),style=MaterialTheme.typography.bodySmall)
                            if(t.isSsh) Text(if(t.hostKey.isEmpty()) label(R.string.host_key_pending) else label(R.string.host_key_saved),style=MaterialTheme.typography.bodySmall)
                            Button(onClick={
                                sending=t.id
                                scope.launch {
                                    val result=withContext(Dispatchers.IO) { runCatching { if(t.isSsh) SshWake.run(t) else WakePacket.send(t.host,t.port,t.mac) } }
                                    sending=null
                                    val failure=result.exceptionOrNull()
                                    if(failure is RouterKeyRequired) keyRequest=t to failure
                                    else snack.showSnackbar(if(result.isSuccess) {
                                        if(t.isSsh) strings.getString(R.string.wake_ssh_sent) else strings.getString(R.string.wake_udp_sent)
                                    } else failure.toUiText(R.string.wake_failed).resolve(strings))
                                }
                            },enabled=sending==null,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                                Text(if(sending==t.id) label(R.string.connecting) else label(R.string.wake))
                            }
                            Row {
                                TextButton(onClick={editing=t},enabled=sending==null) { Text(label(R.string.edit)) }
                                TextButton(onClick={deleting=t},enabled=sending==null) { Text(label(R.string.remove)) }
                            }
                        }
                    }
                }
            }
        }
        if(adding || editing!=null) Editor(editing,onOpenKeys={showDeviceKeys=true},onDismiss={adding=false;editing=null},onSave={t ->
            val next=if(targets.any { it.id==t.id }) targets.map { if(it.id==t.id)t else it } else targets+t
            withContext(Dispatchers.IO) { store.save(next) }
            targets=next;adding=false;editing=null
        })
        keyRequest?.let { (t,request) ->
            RouterKeyDialog(request,onDismiss={keyRequest=null},onTrust={
                keyRequest=null
                scope.launch {
                    val next=targets.map { if(it.id==t.id) it.copy(hostKey=request.receivedKey) else it }
                    val result=withContext(Dispatchers.IO) { runCatching { store.save(next) } }
                    if(result.isSuccess) { targets=next;snack.showSnackbar(strings.getString(R.string.key_saved_notice)) }
                    else snack.showSnackbar(strings.getString(R.string.key_save_failed))
                }
            })
        }
        deleting?.let { t -> AlertDialog(onDismissRequest={deleting=null},title={Text(label(R.string.runtime_remove, t.name))},text={Text(label(R.string.remove_profile_confirm))},confirmButton={TextButton(onClick={
            deleting=null
            scope.launch {
                val next=targets.filterNot {it.id==t.id}
                val result=withContext(Dispatchers.IO) { runCatching { store.save(next) } }
                if(result.isSuccess) {targets=next} else snack.showSnackbar(strings.getString(R.string.remove_failed))
            }
        }){Text(label(R.string.remove))}},dismissButton={TextButton(onClick={deleting=null}){Text(label(R.string.cancel))}}) }
    }
}

@Composable private fun RouterKeyDialog(request: RouterKeyRequired,onDismiss:()->Unit,onTrust:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(request.changed) label(R.string.key_changed) else label(R.string.verify_router))},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(request.changed) label(R.string.key_changed_detail)
                else label(R.string.verify_key_detail))
            Text(SshWake.fingerprint(request.receivedKey),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={if(!request.changed) TextButton(onClick=onTrust){Text(label(R.string.trust_key))}
        else TextButton(onClick=onDismiss){Text(label(R.string.close))}},dismissButton={if(!request.changed)TextButton(onClick=onDismiss){Text(label(R.string.cancel))}})
}

@Composable private fun Editor(target: Target?,onOpenKeys:()->Unit,onDismiss:()->Unit,onSave:suspend (Target)->Unit) {
    var name by rememberSaveable { mutableStateOf(target?.name ?: "") }
    var host by rememberSaveable { mutableStateOf(target?.host ?: "") }
    var mode by rememberSaveable { mutableStateOf(target?.mode ?: "ssh") }
    var port by rememberSaveable { mutableStateOf(target?.port?.toString() ?: "") }
    var mac by rememberSaveable { mutableStateOf(target?.mac ?: "") }
    var username by rememberSaveable { mutableStateOf(target?.username ?: "") }
    var auth by rememberSaveable { mutableStateOf(target?.auth ?: "key") }
    // Never put a password in saved activity state.
    var password by remember { mutableStateOf(target?.password ?: "") }
    var presetJson by rememberSaveable {
        mutableStateOf((target?.routerPreset ?: RouterPreset.asus).json().toString())
    }
    val preset = remember(presetJson) { RouterPreset.fromJson(org.json.JSONObject(presetJson)) }
    var choosingPreset by rememberSaveable { mutableStateOf(false) }
    var iface by rememberSaveable { mutableStateOf(target?.lanInterface ?: "br0") }
    var broadcast by rememberSaveable { mutableStateOf(target?.broadcast ?: false) }
    var hostKey by rememberSaveable { mutableStateOf(target?.hostKey ?: "") }
    var error by remember { mutableStateOf<UiText?>(null) }
    var busy by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<RouterKeyRequired?>(null) }
    val scope=rememberCoroutineScope()
    val ssh=mode=="ssh"
    fun form(requirePassword: Boolean): Target {
        requireText(name.trim().isNotEmpty(), R.string.missing_name)
        WakePacket.validateHost(host.trim()); WakePacket.create(mac.trim())
        val p = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw UiFailure(R.string.invalid_port)
        if(ssh) {
            requireText(username.trim().isNotEmpty(), R.string.missing_username)
            requireText(!requirePassword || auth=="key" || password.isNotEmpty(), R.string.missing_password)
            if(requirePassword && auth=="key") requireText(DeviceSshKey.exists(), R.string.missing_device_key)
            if (requirePassword) preset.command(mac.trim(), iface.trim(), broadcast)
        }
        return Target(target?.id ?: UUID.randomUUID().toString(),name.trim(),host.trim(),p,mac.trim().uppercase().replace('-',':'),mode,
            if(ssh)username.trim() else "",if(ssh && auth=="password")password else "",iface.trim(),broadcast,if(ssh)hostKey else "",auth,preset)
    }
    val editorScroll = rememberScrollState()
    if (!choosingPreset) AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(if(target==null)label(R.string.add_computer) else label(R.string.edit_computer))},text={
        Column(Modifier.verticalScroll(editorScroll),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=ssh,onClick={if(!busy && mode!="ssh"){mode="ssh";port="";hostKey=""}},label={Text(label(R.string.ssh_router))})
                FilterChip(selected=!ssh,onClick={if(!busy && mode!="udp"){mode="udp";port="9";hostKey=""}},label={Text("UDP")})
            }
            OutlinedTextField(name,{name=it},label={Text(label(R.string.name))},singleLine=true,enabled=!busy)
            OutlinedTextField(host,{host=it;hostKey=""},label={Text(if(ssh)label(R.string.router_host) else label(R.string.host))},singleLine=true,enabled=!busy)
            OutlinedTextField(port,{port=it;hostKey=""},label={Text(if(ssh)label(R.string.ssh_port) else label(R.string.udp_port))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=!busy)
            OutlinedTextField(mac,{mac=it},label={Text(label(R.string.computer_mac))},supportingText={Text(label(R.string.mac_hint))},singleLine=true,enabled=!busy)
            if(ssh) {
                OutlinedTextField(username,{username=it},label={Text(label(R.string.ssh_user))},singleLine=true,enabled=!busy)
                Text(label(R.string.authentication),style=MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=auth=="key",onClick={auth="key"},enabled=!busy,label={Text(label(R.string.device_key))})
                    FilterChip(selected=auth=="password",onClick={auth="password"},enabled=!busy,label={Text("Password")})
                }
                if(auth=="key") {
                    Text(label(R.string.authorize_device_key),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick=onOpenKeys,enabled=!busy){Text(label(R.string.generate_copy_key))}
                } else {
                    OutlinedTextField(password,{password=it},label={Text(label(R.string.ssh_password))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!busy)
                    Text(label(R.string.encrypted_credentials),style=MaterialTheme.typography.bodySmall)
                }
                Text(label(R.string.wake_command), style = MaterialTheme.typography.labelLarge)
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                OutlinedButton(onClick = {
                    keyboard?.hide()
                    focus.clearFocus()
                    choosingPreset = true
                }, enabled = !busy) {
                    Text(preset.name)
                }
                Text(preset.template, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preset.usesInterface) {
                    OutlinedTextField(iface, { iface = it }, label = { Text(label(R.string.router_interface)) },
                        singleLine = true, enabled = !busy)
                }
                if (preset.supportsBroadcast) {
                    Row {
                        Checkbox(checked = broadcast, onCheckedChange = { broadcast = it }, enabled = !busy)
                        Text(label(R.string.broadcast), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if(hostKey.isNotEmpty()) {
                    Text(label(R.string.runtime_fingerprint, SshWake.fingerprint(hostKey)),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={hostKey=""},enabled=!busy){Text(label(R.string.forget_key))}
                } else TextButton(onClick={
                    val t=runCatching { form(false) }.getOrElse {error=it.toUiText(R.string.invalid_preset);return@TextButton}
                    busy=true;error=null
                    scope.launch {
                        val result=withContext(Dispatchers.IO){runCatching{SshWake.run(t,probe=true)}}
                        busy=false
                        val failure=result.exceptionOrNull()
                        if(failure is RouterKeyRequired)candidate=failure else error=failure.toUiText(R.string.key_not_received)
                    }
                },enabled=!busy){Text(if(busy)label(R.string.connecting) else label(R.string.verify_ssh_key))}
            } else Text(label(R.string.udp_hint),style=MaterialTheme.typography.bodySmall)
            error?.let {Text(label(it.id, *it.args),color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(onClick={
        val t=runCatching{form(true)}.getOrElse{error=it.toUiText(R.string.invalid_preset);return@TextButton}
        busy=true;error=null
        scope.launch {
            runCatching{onSave(t)}.onFailure{error=it.toUiText(R.string.save_failed)}
            busy=false
        }
    },enabled=!busy){Text(if(busy)label(R.string.wait) else label(R.string.save))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text(label(R.string.cancel))}})
    if (choosingPreset) RouterPresetPicker(
        onDismiss = { choosingPreset = false },
        onSelect = { selected ->
            if (selected != preset) {
                iface = selected.defaultInterface
                broadcast = false
                presetJson = selected.json().toString()
            }
            error = null
            choosingPreset = false
        }
    )
    candidate?.let { request -> RouterKeyDialog(request,onDismiss={candidate=null},onTrust={hostKey=request.receivedKey;candidate=null}) }
}


@Composable private fun DeviceKeyDialog(onDismiss:()->Unit) {
    var publicLine by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val scope=rememberCoroutineScope();val context=LocalContext.current
    val strings = LocalWoWResources.current
    LaunchedEffect(Unit) {
        val result=withContext(Dispatchers.IO){runCatching{if(DeviceSshKey.exists())DeviceSshKey.publicLine() else null}}
        publicLine=result.getOrNull();if(result.isFailure)error=UiText(R.string.device_key_unreadable)
        loading=false
    }
    AlertDialog(onDismissRequest={if(!loading)onDismiss()},title={Text(label(R.string.device_ssh_key))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(label(R.string.device_key_info))
            if(loading) Text(label(R.string.wait))
            publicLine?.let { line ->
                Text("ECDSA P-256 · ${SshWake.fingerprint(line.split(' ')[1])}",style=MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(line,style=MaterialTheme.typography.bodySmall) }
                Text(label(R.string.uninstall_key_warning),style=MaterialTheme.typography.bodySmall)
            }
            error?.let {Text(label(it.id, *it.args),color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={
        if(publicLine==null) TextButton(onClick={
            loading=true;error=null
            scope.launch {
                val result=withContext(Dispatchers.IO){runCatching{DeviceSshKey.generate()}}
                publicLine=result.getOrNull();if(result.isFailure)error=UiText(R.string.key_generation_failed)
                loading=false
            }
        },enabled=!loading){Text(label(R.string.generate_key))}
        else TextButton(onClick={
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(strings.getString(R.string.clipboard_key_label),publicLine!!))
        },enabled=!loading){Text(label(R.string.copy_public_key))}
    },dismissButton={TextButton(onClick=onDismiss,enabled=!loading){Text(label(R.string.close))}})
}
