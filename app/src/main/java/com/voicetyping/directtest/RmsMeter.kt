package com.voicetyping.directtest

import kotlin.math.sqrt

object RmsMeter {
    fun level(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (index in 0 until count.coerceAtMost(samples.size)) {
            val value = samples[index].toDouble() / Short.MAX_VALUE
            sum += value * value
        }
        return sqrt(sum / count.coerceAtMost(samples.size)).toFloat().coerceIn(0f, 1f)
    }
}
