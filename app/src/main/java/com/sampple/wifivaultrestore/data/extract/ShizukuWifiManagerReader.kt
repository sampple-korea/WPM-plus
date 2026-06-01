package com.sampple.wifivaultrestore.data.extract

import android.content.AttributionSource
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.sampple.wifivaultrestore.data.CredentialSource
import com.sampple.wifivaultrestore.data.SecurityType
import com.sampple.wifivaultrestore.data.WifiCredential
import com.sampple.wifivaultrestore.shizuku.PrivilegeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.BitSet

data class PrivilegedWifiManagerRead(
    val credentials: List<WifiCredential>,
    val notes: List<String>,
)

class ShizukuWifiManagerReader(private val context: Context) {
    suspend fun read(mode: PrivilegeMode, source: CredentialSource): PrivilegedWifiManagerRead =
        withContext(Dispatchers.IO) {
            val result = runCatching {
                enableHiddenApiAccess()
                val wifiManager = wifiManagerProxy()
                val shizukuUid = Shizuku.getUid()
                val callerName = callerNameFor(shizukuUid)
                val rawConfigs = invokePrivilegedConfiguredNetworks(
                    wifiManager = wifiManager,
                    callerName = callerName,
                    featureId = SHELL_PACKAGE,
                    shizukuUid = shizukuUid,
                )
                val credentials = rawConfigs.mapNotNull { it.toCredential(source) }.distinctBy { it.id }
                PrivilegedWifiManagerRead(
                    credentials = credentials,
                    notes = listOf(
                        SystemWifiExtractor.note("extract.privileged_success", credentials.size.toString(), mode.name),
                        SystemWifiExtractor.note("extract.privileged_passwords", credentials.count { it.hasPassword }.toString()),
                    ),
                )
            }

            result.getOrElse { error ->
                PrivilegedWifiManagerRead(
                    credentials = emptyList(),
                    notes = listOf(
                        SystemWifiExtractor.note("extract.privileged_unavailable", error.javaClass.simpleName),
                    ),
                )
            }
        }

    private fun wifiManagerProxy(): Any {
        val binder = SystemServiceHelper.getSystemService(Context.WIFI_SERVICE)
            ?: error("Wi-Fi service binder is null.")
        val stubClass = Class.forName("android.net.wifi.IWifiManager\$Stub")
        val asInterface = declaredMethod(stubClass, "asInterface", IBinder::class.java)
        return asInterface.invoke(null, ShizukuBinderWrapper(binder))
            ?: error("IWifiManager proxy is null.")
    }

    private fun invokePrivilegedConfiguredNetworks(
        wifiManager: Any,
        callerName: String,
        featureId: String,
        shizukuUid: Int,
    ): List<Any> {
        val managerClass = Class.forName("android.net.wifi.IWifiManager")
        val attempts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    MethodAttempt(
                        parameterTypes = arrayOf(String::class.java, String::class.java, Bundle::class.java),
                        args = arrayOf(callerName, featureId, attributionBundle(shizukuUid)),
                    ),
                )
            }
            add(
                MethodAttempt(
                    parameterTypes = arrayOf(String::class.java, String::class.java),
                    args = arrayOf(callerName, featureId),
                ),
            )
            add(
                MethodAttempt(
                    parameterTypes = arrayOf(String::class.java),
                    args = arrayOf(callerName),
                ),
            )
        }

        var lastFailure: Throwable? = null
        for (attempt in attempts) {
            val result = runCatching {
                declaredMethod(
                    managerClass,
                    "getPrivilegedConfiguredNetworks",
                    *attempt.parameterTypes,
                ).invoke(wifiManager, *attempt.args)
            }
            val value = result.getOrNull()
            if (result.isSuccess && value != null) return resultToList(value)
            lastFailure = result.exceptionOrNull()
        }
        throw IllegalStateException(
            "getPrivilegedConfiguredNetworks reflection failed.",
            lastFailure,
        )
    }

    private fun resultToList(result: Any): List<Any> {
        if (result is List<*>) return result.filterNotNull()
        val getList = runCatching { declaredMethod(result.javaClass, "getList") }
            .getOrElse { result.javaClass.methods.firstOrNull { it.name == "getList" && it.parameterCount == 0 } }
            ?: error("No getList method on ${result.javaClass.name}.")
        val list = getList.invoke(result) as? List<*>
        return list.orEmpty().filterNotNull()
    }

    private fun Any.toCredential(source: CredentialSource): WifiCredential? {
        val ssid = printableSsid().takeIf { it.isNotBlank() } ?: return null
        val security = securityTypes()
        val password = passwordValue()
        return WifiCredential.create(
            ssid = ssid,
            security = security,
            password = password,
            hidden = fieldValue<Boolean>("hiddenSSID") ?: false,
            autoJoin = fieldValue<Boolean>("allowAutojoin") ?: true,
            source = source,
        )
    }

    private fun Any.printableSsid(): String {
        val fromMethod = runCatching {
            declaredMethod(javaClass, "getPrintableSsid").invoke(this) as? String
        }.getOrNull()
        val raw = fromMethod ?: fieldValue<String>("SSID").orEmpty()
        return raw.stripWifiQuotes()
    }

    private fun Any.passwordValue(): String? {
        val psk = fieldValue<String>("preSharedKey").cleanSecret()
        if (psk != null) return psk
        val wepKeys = fieldValue<Array<String?>>("wepKeys")
        return wepKeys?.firstNotNullOfOrNull { it.cleanSecret() }
    }

    private fun Any.securityTypes(): Set<SecurityType> {
        val keyMgmt = fieldValue<BitSet>("allowedKeyManagement")
        val types = buildSet {
            if (keyMgmt?.get(keyMgmtConstant("SAE", 8)) == true) add(SecurityType.WPA3)
            if (keyMgmt?.get(keyMgmtConstant("OWE", 9)) == true) add(SecurityType.OWE)
            if (
                keyMgmt?.get(keyMgmtConstant("WPA_PSK", 1)) == true ||
                keyMgmt?.get(keyMgmtConstant("WPA2_PSK", 4)) == true
            ) {
                add(SecurityType.WPA2)
            }
            if (
                keyMgmt?.get(keyMgmtConstant("WPA_EAP", 2)) == true ||
                keyMgmt?.get(keyMgmtConstant("IEEE8021X", 3)) == true
            ) {
                add(SecurityType.EAP)
            }
            if (fieldValue<Array<String?>>("wepKeys")?.any { !it.cleanSecret().isNullOrBlank() } == true) {
                add(SecurityType.WEP)
            }
            if (isEmpty()) add(SecurityType.OPEN)
        }
        return types
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.fieldValue(name: String): T? {
        val field = field(javaClass, name) ?: return null
        return runCatching { field.get(this) as? T }.getOrNull()
    }

    private fun field(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            val found = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (found != null) {
                found.isAccessible = true
                return found
            }
            current = current.superclass
        }
        return null
    }

    private fun declaredMethod(type: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
        val method = runCatching {
            HiddenApiBypass.getDeclaredMethod(type, name, *parameterTypes)
        }.getOrElse {
            type.getDeclaredMethod(name, *parameterTypes)
        }
        method.isAccessible = true
        return method
    }

    private fun keyMgmtConstant(name: String, fallback: Int): Int {
        return runCatching {
            Class.forName("android.net.wifi.WifiConfiguration\$KeyMgmt").getField(name).getInt(null)
        }.getOrDefault(fallback)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun attributionBundle(shizukuUid: Int): Bundle {
        val source = AttributionSource.Builder(shizukuUid)
            .setPackageName(SHELL_PACKAGE)
            .setAttributionTag(context.packageName)
            .build()
        return Bundle().apply {
            putParcelable("EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE", source)
        }
    }

    private fun enableHiddenApiAccess() {
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/net/wifi/",
                "Landroid/content/AttributionSource;",
                "Landroid/content/pm/",
                "Lcom/android/modules/utils/",
            )
        }
    }

    private fun callerNameFor(uid: Int): String = when (uid) {
        0 -> "root"
        1000 -> "system"
        2000 -> "shell"
        else -> context.packageName
    }

    private data class MethodAttempt(
        val parameterTypes: Array<Class<*>>,
        val args: Array<Any?>,
    )

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
    }
}

private fun String?.cleanSecret(): String? {
    val value = this?.trim().orEmpty()
    if (value.isBlank() || value == "*") return null
    return value.stripWifiQuotes()
}

private fun String.stripWifiQuotes(): String {
    if (length >= 2 && first() == '"' && last() == '"') {
        return substring(1, length - 1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
    return this
}
