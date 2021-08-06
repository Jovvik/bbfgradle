package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import org.jetbrains.kotlin.psi.KtExpression

class ExpressionGenerator(val body: KtExpression) {
    fun generate() {
        addToBody(Factory.psiFactory.createExpression("TODO()"))
    }

    private fun addToBody(expression: KtExpression) {
        body.addBefore(expression, body.lastChild)
        body.addBefore(Factory.psiFactory.createWhiteSpace("\n"), body.lastChild)
    }
}