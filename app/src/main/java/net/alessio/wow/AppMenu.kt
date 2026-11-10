package net.alessio.wow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppMenu(
    italian: Boolean,
    setItalian: (Boolean) -> Unit,
    checkUpdates: () -> Unit,
    content: @Composable (() -> Unit) -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                ModalDrawerSheet(drawerState = drawer) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text(label(R.string.menu), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 20.dp))
                        Text(label(R.string.language), style = MaterialTheme.typography.labelLarge)
                        NavigationDrawerItem(
                            label = { Text(label(R.string.language_en)) },
                            selected = !italian,
                            onClick = { setItalian(false) }
                        )
                        NavigationDrawerItem(
                            label = { Text(label(R.string.language_it)) },
                            selected = italian,
                            onClick = { setItalian(true) }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    NavigationDrawerItem(
                        label = { Text(label(R.string.update_check)) },
                        selected = false,
                        onClick = { scope.launch { drawer.close(); checkUpdates() } }
                    )
                        NavigationDrawerItem(
                            label = { Text(label(R.string.third_party_licenses)) },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawer.close()
                                    showLicenses = true
                                }
                            }
                        )
                    }
                }
            }
        ) {
            content { scope.launch { drawer.open() } }
        }
    }
    if (showLicenses) ThirdPartyLicenses(italian) { showLicenses = false }
}

@Composable
internal fun MenuButton(onClick: () -> Unit) {
    val description = label(R.string.menu)
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.size(24.dp)) {
            for (y in listOf(6.dp, 12.dp, 18.dp)) {
                drawLine(color, Offset(3.dp.toPx(), y.toPx()), Offset(21.dp.toPx(), y.toPx()),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

private val notices = listOf(
    "JSch" to "JSch.txt",
    "JZlib" to "JZlib.txt",
    "jBCrypt" to "jBCrypt.txt",
    "Bouncy Castle" to "BouncyCastle.txt",
    "AndroidX / Kotlin / Apache 2.0" to "Apache-2.0.txt",
    "Kotlin / Boost" to "Kotlin-Boost.txt",
    "Kotlin / ThreeTen" to "Kotlin-ThreeTen.txt"
)

@Composable
private fun ThirdPartyLicenses(italian: Boolean, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val notice = selected?.let(notices::get)
    AlertDialog(
        onDismissRequest = { if (notice == null) onDismiss() else selected = null },
        title = { Text(notice?.first ?: label(R.string.third_party_licenses)) },
        text = {
            if (notice == null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    notices.forEachIndexed { index, (name, _) ->
                        TextButton(onClick = { selected = index }) { Text(name) }
                    }
                }
            } else {
                val locale = if (italian) "it" else "en"
                key(locale, notice.second) { LicenseText(locale + "/" + notice.second) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(label(R.string.close)) }
        },
        dismissButton = {
            if (notice != null) TextButton(onClick = { selected = null }) { Text(label(R.string.back)) }
        }
    )
}

@Composable
private fun LicenseText(file: String) {
    val assets = LocalContext.current.assets
    val text by produceState<Result<String>?>(null, assets, file) {
        value = withContext(Dispatchers.IO) {
            runCatching { assets.open("licenses/" + file).bufferedReader().use { it.readText() } }
        }
    }
    val result = text
    when {
        result == null -> Text(label(R.string.wait))
        result.isFailure -> Text(label(R.string.license_unreadable))
        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
            SelectionContainer { Text(result.getOrThrow(), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
