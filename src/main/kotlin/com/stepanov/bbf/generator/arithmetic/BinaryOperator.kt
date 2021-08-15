package com.stepanov.bbf.generator.arithmetic

import com.intellij.util.containers.ComparatorUtil.max
import com.stepanov.bbf.generator.Context
import com.stepanov.bbf.generator.Policy

sealed class BinaryOperator(context: Context, depth: Int) : Node(context, depth + 1) {
    private var left = Policy.Arithmetic.node(context, depth + 1)
    private var right = Policy.Arithmetic.node(context, depth + 1)

    init {
        if (left.type.isUnsigned xor right.type.isUnsigned) {
            right = CastOperator(
                context,
                depth + 1,
                right,
                if (left.type.isUnsigned) Policy.Arithmetic.unsignedType() else Policy.Arithmetic.signedType()
            )
        }
    }

    abstract val symbol: String

    override fun toString() = "($left $symbol $right)"
    override val type = max(left.type, right.type)
}

class Addition(context: Context, depth: Int) : BinaryOperator(context, depth + 1) {
    override val symbol = "+"
}

class Subtraction(context: Context, depth: Int) : BinaryOperator(context, depth + 1) {
    override val symbol = "-"
}

class Multiplication(context: Context, depth: Int) : BinaryOperator(context, depth + 1) {
    override val symbol = "*"
}

class Division(context: Context, depth: Int) : BinaryOperator(context, depth + 1) {
    override val symbol = "/"
}