package com.sampple.wifivaultrestore.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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
        runCatching {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    suspend fun run(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(SERVICE_TIMEOUT_MILLIS) {
                val bound = bindService()
                try {
                    val raw = bound.service.run(command)
                    val json = JSONObject(raw)
                    ShellCommandResult(
                        exitCode = json.optInt("exitCode", -1),
                        output = json.optString("output"),
                        error = json.optString("error"),
                        elapsedMillis = json.optLong("elapsedMillis"),
                    )
                } finally {
                    bound.close()
                }
            }
        }.getOrElse { error ->
            ShellCommandResult(
                exitCode = -1,
                output = "",
                error = error.javaClass.simpleName + ": " + (error.message ?: "Shizuku command failed"),
                elapsedMillis = 0,
            )
        }
    }

    private suspend fun bindService(): BoundShizukuService = suspendCancellableCoroutine { continuation ->
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuShellService::class.java.name),
        )
            .daemon(false)
            .tag("wifi-extract-shell")
            .version(1)
            .processNameSuffix("extract")

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val service = IShizukuShellService.Stub.asInterface(binder)
                if (continuation.isActive) {
                    continuation.resume(BoundShizukuService(args, this, service))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (continuation.isActive) {
                    continuation.resumeWith(
                        Result.failure(IllegalStateException("Shizuku user service disconnected.")),
                    )
                }
            }
        }

        continuation.invokeOnCancellation {
            runCatching { Shizuku.unbindUserService(args, connection, false) }
        }
        runCatching {
            Shizuku.bindUserService(args, connection)
        }.onFailure { error ->
            if (continuation.isActive) {
                continuation.resumeWith(Result.failure(error))
            }
        }
    }

    private data class BoundShizukuService(
        val args: Shizuku.UserServiceArgs,
        val connection: ServiceConnection,
        val service: IShizukuShellService,
    ) {
        fun close() {
            runCatching { Shizuku.unbindUserService(args, connection, false) }
        }
    }

    private companion object {
        const val SERVICE_TIMEOUT_MILLIS = 30_000L
    }
}
