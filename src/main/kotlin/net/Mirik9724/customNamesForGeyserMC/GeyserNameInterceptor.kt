package net.Mirik9724.customNamesForGeyserMC

import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.SuperCall
import net.bytebuddy.implementation.bind.annotation.This
import java.util.concurrent.Callable

object GeyserNameInterceptor {

    @JvmStatic
    @RuntimeType
    fun intercept(
        @This authData: Any,
        @SuperCall superCall: Callable<String>
    ): String {
        val originalName = superCall.call() as String
        val plugin = CustomNamesForGeyserMC.instanceRef.get() as? CustomNamesForGeyserMC ?: return originalName

        try {
            val xuidMethod = authData.javaClass.getMethod("xuid", *arrayOf<Class<*>>())
            xuidMethod.isAccessible = true
            val playerXuid = xuidMethod.invoke(authData, *arrayOf<Any>()) as String

            if (!originalName.startsWith("CNFG_") && !plugin.originalNames.containsKey(playerXuid)) {
                plugin.originalNames[playerXuid] = originalName
            }

            val stackTrace = Thread.currentThread().stackTrace
            var isLoginProcess = false

            for (element in stackTrace) {
                val className = element.className
                val methodName = element.methodName

                if (className.contains("UpstreamPacketHandler") ||
                    className.contains("Login") ||
                    methodName.contains("createProfile") ||
                    methodName.contains("initialize")
                ) {
                    isLoginProcess = true
                    break
                }
            }

            if (!isLoginProcess) {
                return originalName
            }

            if (plugin.linkingXUID.containsKey(playerXuid)) {
                val customNick = plugin.linkingXUID[playerXuid]
                if (customNick != null) {
                    return customNick
                }
            }

            val nameForHash = plugin.originalNames[playerXuid] ?: originalName
            val hash = Math.abs(nameForHash.replace(".", "").hashCode() % 100000)
            return "CNFG_$hash"

        } catch (e: Exception) {
            return originalName
        }
    }
}
