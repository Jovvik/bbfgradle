package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context

sealed class Node(val context: Context, val depth: Int) {
    abstract override fun toString(): String
    abstract val type: Type
}