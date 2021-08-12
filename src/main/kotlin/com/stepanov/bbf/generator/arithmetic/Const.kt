package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context
import com.stepanov.bbf.generator.Policy

class Const(context: Context, depth: Int) : Node(context, depth + 1) {
    val value = Policy.Arithmetic.const()

    override fun toString() = value
}