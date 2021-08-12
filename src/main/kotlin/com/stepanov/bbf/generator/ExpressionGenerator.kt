package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import org.jetbrains.kotlin.psi.KtExpression

class ExpressionGenerator(val body: KtExpression, val context: Context) {
    fun generateArithmetic(index: Int) {
        Factory.psiFactory.createProperty(
            "variable$index",
            null,
            Policy.isVar(),
            Policy.Arithmetic.node(context).toString()
        ).let {
            context.visibleVariables.add(it)
            addToBody(it)
        }
    }

    fun generateTodo() {
        addToBody("TODO()")
    }

    private fun addToBody(expression: String) = addToBody(Factory.psiFactory.createExpression(expression))

    private fun addToBody(expression: KtExpression) {
        body.addBefore(expression, body.lastChild)
        body.addBefore(Factory.psiFactory.createWhiteSpace("\n"), body.lastChild)
    }
}