package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class Variable(context: Context, val value: KtCallableDeclaration, depth: Int = 0) : Node(context, depth + 1) {
    constructor(context: Context, depth: Int) : this(context, context.visibleNumericVariables.random(), depth)

    override fun toString() = value.name!!
    override val type = Type.values().first { it.toString() == value.typeReference?.text }
}