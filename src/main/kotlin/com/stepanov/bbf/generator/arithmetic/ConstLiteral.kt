package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context
import com.stepanov.bbf.generator.Policy

class ConstLiteral(context: Context, depth: Int) : Node(context, depth + 1) {
    override val type = Policy.Arithmetic.type()
    private val value = Policy.Arithmetic.constLiteral(type)
    override fun toString() = value
}