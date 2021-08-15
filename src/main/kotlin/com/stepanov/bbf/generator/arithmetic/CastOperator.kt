package com.stepanov.bbf.generator.arithmetic

import com.stepanov.bbf.generator.Context
import com.stepanov.bbf.generator.Policy

class CastOperator(context: Context, depth: Int, childNode_: Node?, type_: Type?) : Node(context, depth + 1) {

    private var childNode = childNode_ ?: Policy.Arithmetic.node(context, depth + 1)

    override var type = type_ ?: Policy.Arithmetic.type()

    init {
        // `LONG` because casts like `Float` to `Short` are illegal.
        if (type.isUnsigned && childNode.type.isFloatingPoint) {
            childNode = CastOperator(context, depth + 1, childNode, Type.LONG)
        } else if (type.isFloatingPoint && childNode.type.isUnsigned) {
            childNode = CastOperator(context, depth + 1, childNode, Type.LONG)
        }
    }

    override fun toString() = "$childNode.to$type()"
}