package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator

class SortStatusByTime(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        runCatching {
            val statusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader)
            statusListUpdatesClass.resolve().constructor {}.hookAll {
                before {
                    for (i in args.indices) {
                        val list = args[i] as? List<*> ?: continue
                        if (list.size < 2) continue

                        val keyed = list.map { it to timeOf(it) }
                        if (keyed.any { it.first == null || it.second <= 0L }) continue

                        val sorted = keyed.sortedByDescending { it.second }.map { it.first }

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