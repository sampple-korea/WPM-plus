package com.sampple.wifivaultrestore.shizuku

import android.content.Context
import android.system.Os
import androidx.annotation.Keep
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ShizukuShellService : IShizukuShellService.Stub() {
    constructor()

    @Keep
    constructor(context: Context)

    override fun destroy() {
        System.exit(0)
    }

    override fun uid(): Int = Os.getuid()

    override fun run(command: String): String {
        val started = System.currentTimeMillis()
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val readerThread = Thread {
                reader.useLines { lines ->
                    lines.forEach { line ->
                        if (output.length < MAX_OUTPUT_CHARS) {
                            output.appendLine(line)
                        }
                    }
                }
            }
            readerThread.start()

            val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                readerThread.join(500)
                resultJson(-1, output.toString(), started, "Command timed out.")
            } else {
                readerThread.join(500)
                resultJson(process.exitValue(), output.toString(), started, null)
            }
        } catch (throwable: Throwable) {
            resultJson(-1, "", started, throwable.javaClass.simpleName + ": " + throwable.message)
        }
    }

    private fun resultJson(exitCode: Int, output: String, started: Long, error: String?): String {
        return JSONObject()
            .put("exitCode", exitCode)
            .put("output", output)
            .put("error", error ?: "")
            .put("elapsedMillis", System.currentTimeMillis() - started)
            .toString()
    }

    private companion object {
        const val MAX_OUTPUT_CHARS = 1_000_000
        const val TIMEOUT_SECONDS = 20L
    }
}
