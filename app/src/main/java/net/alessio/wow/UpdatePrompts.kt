package net.alessio.wow

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun UpdatePrompts(
    activity: ComponentActivity,
    content: @Composable (() -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<ReleaseUpdate?>(null) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var installFile by remember { mutableStateOf<File?>(null) }
    var checking by remember { mutableStateOf(false) }
    var reportCheckResult by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var readingDownload by remember { mutableStateOf(false) }
    var offeredId by remember { mutableLongStateOf(-1L) }
    var refreshRequested by remember { mutableStateOf(false) }

    fun readDownload(manual: Boolean = false) {
        val id = UpdateInstaller.pending(activity)
        if (id < 0 || (!manual && offeredId == id)) return
        if (readingDownload) {
            refreshRequested = true
            return
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        readingDownload = true
        scope.launch {
            try {
                val apk = UpdateInstaller.completed(activity, id)
                if (apk != null) {
                    message = null
                    installFile = apk
                    offeredId = id
                } else if (manual) {
                    message = UiText(if (UpdateInstaller.pending(activity) == id) {
                        R.string.update_downloading
                    } else {
                        R.string.update_none
                    })
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                message = failure.toUiText(R.string.update_install_failed)
            } finally {
                readingDownload = false
                if (refreshRequested) {
                    refreshRequested = false
                    readDownload()
                }
            }
        }
    }

    fun checkUpdates(manual: Boolean) {
        if (checking) {
            if (manual) {
                reportCheckResult = true
                message = UiText(R.string.update_checking)
            }
            return
        }
        if (UpdateInstaller.pending(activity) >= 0) {
            if (manual) readDownload(manual = true)
            return
        }
        checking = true
        reportCheckResult = manual
        if (manual) message = UiText(R.string.update_checking)
        scope.launch {
            try {
                available = ReleaseUpdates.check(activity)
                if (reportCheckResult) message = if (available == null) UiText(R.string.update_none) else null
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                if (reportCheckResult) message = failure.toUiText(R.string.update_check_failed)
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(activity) { checkUpdates(manual = false) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) readDownload()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id >= 0 && id == UpdateInstaller.pending(context)) readDownload()
            }
        }
        activity.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(
            activity, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            activity.lifecycle.removeObserver(observer)
            activity.unregisterReceiver(receiver)
        }
    }

    content { checkUpdates(manual = true) }
    available?.let { update ->
        AlertDialog(
            onDismissRequest = { available = null },
            title = { Text(label(R.string.update_available)) },
            text = { Text(label(R.string.update_offer, update.version)) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        UpdateInstaller.remember(activity, ReleaseUpdates.download(activity, update))
                        available = null
                        message = UiText(R.string.update_downloading)
                    } catch (failure: Exception) {
                        available = null
                        message = failure.toUiText(R.string.update_download_failed)
                    }
                }) { Text(label(R.string.update_download)) }
            },
            dismissButton = { TextButton(onClick = { available = null }) { Text(label(R.string.update_later)) } }
        )
    }

    installFile?.let { file ->
        AlertDialog(
            onDismissRequest = { installFile = null },
            title = { Text(label(R.string.update_ready)) },
            text = { Text(label(R.string.update_verified)) },
            confirmButton = {
                TextButton(enabled = !installing, onClick = {
                    installing = true
                    scope.launch {
                        try {
                            if (UpdateInstaller.install(activity, file)) installFile = null
                            else message = UiText(R.string.update_install_permission)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Exception) {
                            installFile = null
                            message = failure.toUiText(R.string.update_install_failed)
                        } finally {
                            installing = false
                        }
                    }
                }) { Text(label(R.string.update_install)) }
            },
            dismissButton = { TextButton(onClick = { installFile = null }) { Text(label(R.string.update_later)) } }
        )
    }

    message?.let { status ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(label(R.string.updates)) },
            text = { Text(label(status.id, *status.args)) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(label(R.string.close)) } }
        )
    }
}
