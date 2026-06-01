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
            appendLine(redactSecretsForReport(trace))
        }
        crashFile(context).writeText(report.take(MAX_REPORT_CHARS), Charsets.UTF_8)
    }

    private fun crashFile(context: Context): File {
        return File(context.applicationContext.filesDir, FILE_NAME)
    }

    private const val MAX_REPORT_CHARS = 64_000

    internal fun redactSecretsForReport(text: String): String {
        val redactedKeyValues = text.lineSequence()
            .joinToString("\n", transform = ::redactSecretKeyValueLine)
        return redactWifiQrPasswords(redactedKeyValues)
    }

    private fun redactSecretKeyValueLine(line: String): String {
        val jsonRedacted = JSON_SECRET_PATTERN.replace(line) { match ->
            val key = match.groups[1]?.value.orEmpty()
            "\"$key\":\"[redacted]\""
        }

        val keyValue = KEY_VALUE_SECRET_PATTERN.find(jsonRedacted) ?: return jsonRedacted
        val key = keyValue.groups[1]?.value.orEmpty()
        val separator = keyValue.groups[2]?.value.orEmpty()
        return jsonRedacted.substring(0, keyValue.range.first) +
            "$key$separator[redacted]"
    }

    private val KEY_VALUE_SECRET_PATTERN = Regex("(?i)\\b(password|passphrase|psk|preSharedKey)(\\s*[:=]\\s*).*$")
    private val JSON_SECRET_PATTERN = Regex("(?i)\"(password|passphrase|psk|preSharedKey)\"\\s*:\\s*\"[^\"]*\"")

    private fun redactWifiQrPasswords(text: String): String {
        return text.lineSequence().joinToString("\n") { line ->
            val wifiStart = line.indexOf("WIFI:", ignoreCase = true)
            if (wifiStart < 0) return@joinToString line

            val directPasswordMarker = line.indexOf("WIFI:P:", startIndex = wifiStart, ignoreCase = true)
            val fieldPasswordMarker = line.indexOf(";P:", startIndex = wifiStart, ignoreCase = true)
            val valueStart = when {
                directPasswordMarker >= 0 -> directPasswordMarker + "WIFI:P:".length
                fieldPasswordMarker >= 0 -> fieldPasswordMarker + 3
                else -> return@joinToString line
            }

            val valueEnd = findWifiQrFieldEnd(line, valueStart)
            line.replaceRange(valueStart, valueEnd, "[redacted]")
        }
    }

    private fun findWifiQrFieldEnd(value: String, start: Int): Int {
        var index = start
        var escaped = false
        while (index < value.length) {
            val char = value[index]
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == ';' -> return index
            }
            index += 1
        }
        return value.length
    }
}
