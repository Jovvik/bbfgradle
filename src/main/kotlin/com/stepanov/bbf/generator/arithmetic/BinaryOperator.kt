package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context
import com.stepanov.bbf.generator.Policy

sealed class BinaryOperator(context: Context, depth: Int) : Node(context, depth + 1) {
    private val left = Policy.Arithmetic.node(context, depth + 1)
    private val right = Policy.Arithmetic.node(context, depth + 1)

    abstract val symbol: String

    override fun toString() = "($left $symbol $right)"
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