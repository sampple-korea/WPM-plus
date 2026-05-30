package com.sampple.wifivaultrestore.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

data class ShizukuState(
    val running: Boolean,
    val permissionGranted: Boolean,
    val uid: Int? = null,
) {
    val mode: PrivilegeMode = when (uid) {
        0 -> PrivilegeMode.Root
        2000 -> PrivilegeMode.Shell
        null -> PrivilegeMode.Unavailable
        else -> PrivilegeMode.Other
    }
}

enum class PrivilegeMode {
    Unavailable,
    Shell,
    Root,
    Other,
}

data class ShellCommandResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val elapsedMillis: Long,
)

class ShizukuCommandRunner(private val context: Context) {
    fun state(): ShizukuState {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) return ShizukuState(running = false, permissionGranted = false)
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (granted) runCatching { Shizuku.getUid() }.getOrNull() else null
        return ShizukuState(running = true, permissionGranted = granted, uid = uid)
    }

    fun requestPermission(requestCode: Int) {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            Shizuku.requestPermission(requestCode)
        }
    }

    suspend fun run(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        val service = bindService()
        val raw = service.run(command)
        val json = JSONObject(raw)
        ShellCommandResult(
            exitCode = json.optInt("exitCode", -1),
            output = json.optString("output"),
            error = json.optString("error"),
            elapsedMillis = json.optLong("elapsedMillis"),
        )
    }

    private suspend fun bindService(): IShizukuShellService = suspendCancellableCoroutine { continuation ->
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuShellService::class.java.name),
        )
            .daemon(false)
            .tag("wifi-extract-shell")
            .version(1)
            .processNameSuffix("extract")

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                continuation.resume(IShizukuShellService.Stub.asInterface(binder))
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        continuation.invokeOnCancellation {
            runCatching { Shizuku.unbindUserService(args, connection, false) }
        }
        Shizuku.bindUserService(args, connection)
    }
}
