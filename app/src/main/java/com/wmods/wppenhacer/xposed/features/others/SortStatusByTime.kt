package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.YukiLog

class SortStatusByTime(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        runCatching {
            val cls = Unobfuscator.loadStatusListUpdatesClass(classLoader)
            cls.resolve().constructor {}.hookAll {
                before {
                    YukiLog.log("SortStatus: ctor args=${args.size}")
                    for (i in args.indices) {
                        val list = args[i] as? List<*> ?: continue
                        val times = list.map { timeOf(it) }
                        YukiLog.log(
                            "SortStatus: arg$i size=${list.size} " +
                                "readable=${times.count { it > 0L }} " +
                                "type=${list.javaClass.name} " +
                                "first=${list.firstOrNull()?.javaClass?.name}"
                        )
                        if (list.size < 2 || times.count { it > 0L } < 2) continue

                        val sorted = list.indices
                            .sortedByDescending { times[it] }
                            .map { list[it] }

                        var done = false
                        if (list is MutableList<*>) {
                            try {
                                @Suppress("UNCHECKED_CAST")
                                val m = list as MutableList<Any?>
                                for (j in sorted.indices) m[j] = sorted[j]
                                done = true
                            } catch (_: Throwable) {
                            }
                        }
                        if (!done) args[i] = sorted
                    }
                }
            }
        }.onFailure { logDebug(it) }
    }

    private fun timeOf(item: Any?): Long {
        return try {
            StatusItemWpp.from(item)?.fMessage?.timeStamp ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }

    override fun getPluginName(): String = "Sort Status By Time"
}
