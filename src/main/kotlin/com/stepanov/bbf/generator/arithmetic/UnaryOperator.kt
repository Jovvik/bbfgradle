package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context
import com.stepanov.bbf.generator.Policy

sealed class UnaryOperator(context: Context, depth: Int) : Node(context, depth + 1) {
    abstract val symbol: String
    protected var child = Policy.Arithmetic.node(context, depth + 1)

    override fun toString() = "($symbol $child)"
}

class Negation(context: Context, depth: Int) : UnaryOperator(context, depth + 1) {
    init {
        if (child.type.isUnsigned) {
            child = CastOperator(context, depth + 1, child, Policy.Arithmetic.signedType())
        }
    }

    override val type = child.type.toSigned()
    override val symbol = "-"
}