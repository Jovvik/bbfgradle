package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context

class Variable(context: Context, depth: Int) : Node(context, depth + 1) {
    val value = context.visibleNumericVariables.random()

    override fun toString() = value.name!!
}