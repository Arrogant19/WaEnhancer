package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SortStatusByTime(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    private var logs = 0
    private val fmt = SimpleDateFormat("dd HH:mm:ss", Locale.US)

    override fun doHook() {
        runCatching {
            val cls = Unobfuscator.loadStatusListUpdatesClass(classLoader)
            cls.resolve().constructor {}.hookAll {
                before {
                    for (i in args.indices) {
                        val list = args[i] as? List<*> ?: continue
                        if (list.size < 2) continue

                        val times = list.map { timeOf(it) }
                        if (times.none { it > 0L }) continue

                        val order = list.indices.sortedByDescending { times[it] }
                        val sorted = order.map { list[it] }

                        if (logs < 30) {
                            logs++
                            try {
                                YukiLog.log(
                                    "SortStatus: arg$i n=${list.size} " +
                                        "before=${show(times)} after=${show(order.map { times[it] })}"
                                )
                            } catch (_: Throwable) {
                            }
                        }

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

    private fun show(l: List<Long>): String =
        l.take(6).joinToString(",") { if (it > 0L) fmt.format(Date(it)) else "?" }

    private fun timeOf(item: Any?): Long {
        val viaWpp = try {
            StatusItemWpp.from(item)?.fMessage?.timeStamp ?: -1L
        } catch (_: Throwable) {
            -1L
        }
        if (viaWpp > 0L) return viaWpp
        val limit = System.currentTimeMillis() + 3_600_000L
        val deep = try {
            deepTime(item, 0, intArrayOf(400), limit)
        } catch (_: Throwable) {
            -1L
        }
        if (deep > 0L) return deep
        return try {
            labelKey(item)
        } catch (_: Throwable) {
            -1L
        }
    }

    // Hanya terima waktu epoch (ms) yang kelipatan 1000 dan tidak di masa depan:
    // itu waktu status asli. Waktu pemrosesan (ada milidetik) dan angka masa depan diabaikan.
    private fun deepTime(obj: Any?, depth: Int, budget: IntArray, limit: Long): Long {
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
                            if (v in 1_600_000_000_000L..limit && v % 1000L == 0L) {
                                best = maxOf(best, v)
                            }
                        }
                        is List<*> -> for (e in v.take(30)) {
                            best = maxOf(best, deepTime(e, depth + 1, budget, limit))
                        }
                        else -> {
                            val n = v.javaClass.name
                            if (!n.startsWith("java.") && !n.startsWith("android.") &&
                                !n.startsWith("kotlin.")
                            ) {
                                best = maxOf(best, deepTime(v, depth + 1, budget, limit))
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

    // Cadangan: ubah teks waktu di layar menjadi perkiraan waktu epoch.
    private fun labelKey(item: Any?): Long {
        if (item == null) return -1L
        var c: Class<*>? = item.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers)) continue
                if (!CharSequence::class.java.isAssignableFrom(f.type)) continue
                try {
                    f.isAccessible = true
                    val s = (f.get(item) as? CharSequence)?.toString()?.trim().orEmpty()
                    if (s.isEmpty()) continue
                    val age = parseAgeMinutes(s)
                    if (age >= 0L) return System.currentTimeMillis() - age * 60_000L
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        return -1L
    }

    private fun parseAgeMinutes(label: String): Long {
        val s = label.lowercase()
        val num = Regex("\\d+").find(s)?.value?.toLongOrNull()
        val clock = Regex("^(\\d{1,2})[:.](\\d{2})$").find(s)
        return when {
            clock != null -> {
                val h = clock.groupValues[1].toInt()
                val m = clock.groupValues[2].toInt()
                val cal = Calendar.getInstance()
                val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val diff = nowMin - (h * 60 + m)
                if (diff >= 0) diff.toLong() else (diff + 1440).toLong()
            }
            s.contains("kemarin") || s.contains("yesterday") -> 1440L
            s.contains("menit") || s.contains("minute") -> num ?: 1L
            s.contains("jam") || s.contains("hour") -> (num ?: 1L) * 60L
            s.contains("detik") || s.contains("second") ||
                s.contains("baru") || s.contains("now") -> 0L
            else -> 2880L
        }
    }

    override fun getPluginName(): String = "Sort Status By Time"
}