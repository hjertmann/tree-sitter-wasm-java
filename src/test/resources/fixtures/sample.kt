package com.example.kotlin

import kotlin.math.abs
import kotlin.math.sqrt

data class Vector2D(val x: Double, val y: Double) {
    fun length(): Double = sqrt(x * x + y * y)
    fun normalize(): Vector2D {
        val len = length()
        return if (len == 0.0) this else Vector2D(x / len, y / len)
    }
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
}

fun distance(a: Vector2D, b: Vector2D): Double {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
