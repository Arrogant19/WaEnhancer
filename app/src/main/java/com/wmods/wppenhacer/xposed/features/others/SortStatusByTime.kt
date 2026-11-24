package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.lang.reflect.Modifier

class SortStatusByTime(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        runCatching {
            val cls = Unobfuscator.loadStatusListUpdatesClass(classLoader)
            cls.resolve().constructor {}.hookAll {
                before {
                    for (i in args.indices) {
                        val list = args[i] as? List<*> ?: continue
                        if (list.size < 2) continue

                        val times = list.map { timeOf(it) }
                        YukiLog.log(
                            "SortStatus: arg$i size=${list.size} readable=${times.count { it > 0L }}"
                        )
                        YukiLog.log("SortStatus: item0 ${describe(list[0])}")
                        if (times.none { it > 0L }) continue

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
        val viaWpp = try {
            StatusItemWpp.from(item)?.fMessage?.timeStamp ?: -1L
        } catch (_: Throwable) {
            -1L
        }
        if (viaWpp > 0L) return viaWpp
        return try {
            deepTime(item, 0, intArrayOf(400))
        } catch (_: Throwable) {
            -1L
        }
    }

    private fun deepTime(obj: Any?, depth: Int, budget: IntArray): Long {
        if (obj == null || depth > 3 || budget[0] <= 0) return -1L
        budget[0]--
        var best = -1L
        var c: Class<*>? = obj.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(obj) ?: continue
                    when (v) {
                        is Long -> {
                            if (v in 1_600_000_000_000L..2_200_000_000_000L) {
                                best = maxOf(best, v)
                            } else if (v in 1_600_000_000L..2_200_000_000L) {
                                best = maxOf(best, v * 1000L)
                            }
                        }
                        is List<*> -> for (e in v.take(30)) {
                            best = maxOf(best, deepTime(e, depth + 1, budget))
                        }
                        else -> {
                            val n = v.javaClass.name
                            if (!n.startsWith("java.") && !n.startsWith("android.") &&
                                !n.startsWith("kotlin.")
                            ) {
                                best = maxOf(best, deepTime(v, depth + 1, budget))
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        return best
    }

    private fun describe(obj: Any?): String {
        if (obj == null) return "null"
        val sb = StringBuilder(obj.javaClass.name).append(" {")
        var c: Class<*>? = obj.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(obj)
                    val shown = when (v) {
                        null -> "null"
                        is Long, is Int, is Boolean, is String -> v.toString().take(40)
                        is List<*> -> "List(${v.size})"
                        else -> v.javaClass.name
                    }
                    sb.append(f.name).append(':').append(f.type.simpleName)
                        .append('=').append(shown).append("; ")
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        return sb.append("}").toString()
    }

    override fun getPluginName(): String = "Sort Status By Time"
}