package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context
import com.stepanov.bbf.generator.Policy

class CastOperator(context: Context, depth: Int, childNode_: Node?, type_: Type?) : Node(context, depth + 1) {

    private var childNode = childNode_ ?: Policy.Arithmetic.node(context, depth + 1)

    override var type = type_ ?: Policy.Arithmetic.type()

    init {
        // see https://youtrack.jetbrains.com/issue/KT-30360
        if (childNode.type.isFloatingPoint && type < Type.INT) {
            childNode = CastOperator(context, depth + 1, childNode, Type.INT)
        }
    }

    override fun toString() = "$childNode.to$type()"
}