package com.sampple.wifivaultrestore.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashReporter {
    private const val FILE_NAME = "last-crash-report.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            if (previous != null && previous !== Thread.getDefaultUncaughtExceptionHandler()) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun consumePendingReport(context: Context): String? {
        val file = crashFile(context)
        if (!file.exists()) return null
        return runCatching {
            val report = file.readText(Charsets.UTF_8)
            file.delete()
            report.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()
        val report = buildString {
            appendLine("WPM+ crash report")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine("Thread: ${thread.name}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Process: ${context.packageName}")
            appendLine()
            appendLine(trace.redactSecrets())
        }
        crashFile(context).writeText(report.take(MAX_REPORT_CHARS), Charsets.UTF_8)
    }

    private fun crashFile(context: Context): File {
        return File(context.applicationContext.filesDir, FILE_NAME)
    }

    private const val MAX_REPORT_CHARS = 64_000

    private fun String.redactSecrets(): String {
        return REDACTION_PATTERNS.fold(this) { current, pattern ->
            pattern.replace(current) { match ->
                val key = match.groups[1]?.value ?: return@replace "[redacted]"
                "$key=[redacted]"
            }
        }
    }

    private val REDACTION_PATTERNS = listOf(
        Regex("(?i)\\b(password|passphrase|psk|preSharedKey)\\s*[:=]\\s*([^\\s,;)}]+)"),
        Regex("(?i)\\\"(password|passphrase|psk|preSharedKey)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""),
    )
}
