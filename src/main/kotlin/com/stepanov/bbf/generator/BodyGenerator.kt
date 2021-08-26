package com.stepanov.bbf.generator

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.RandomInstancesGenerator
import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.generator.arithmetic.CastOperator
import com.stepanov.bbf.generator.arithmetic.Type
import com.stepanov.bbf.generator.arithmetic.Variable
import org.jetbrains.kotlin.builtins.isFunctionTypeOrSubtype
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction

class BodyGenerator(
    val body: KtExpression,
    val context: Context,
    val file: KtFile,
    val returnType: KtTypeOrTypeParam?,
    val containingFunction: KtFunction?
) {
    private fun generateArithmetic(index: Int) {
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

    private fun generateTodo() {
        addToBody("TODO()")
    }

    private fun addToBody(expression: String) = addToBody(Factory.psiFactory.createExpression(expression))

    private fun addToBody(expression: PsiElement) {
        body.addBefore(expression, body.lastChild)
        body.addBefore(Factory.psiFactory.createWhiteSpace("\n"), body.lastChild)
    }

    fun generate() {
        repeat(Policy.arithmeticExpressionLimit()) {
            generateArithmetic(it)
        }
        generatePrint()
        generateReturn()
    }

    private fun generatePrint() {
        for (variable in context.visibleVariables) {
            addToBody("println(${variable.name})")
        }
    }

    private fun generateReturn() {
        val returnVariable =
            context.visibleVariables.filter { (it.typeReference?.text ?: false) == returnType?.name }.randomOrNull()
        val numericReturnType = Type.values().firstOrNull { it.toString() == returnType?.name }
        val numericVariable = context.visibleNumericVariables.randomOrNull()
        when {
            returnType == null -> {
            }
            returnType is KtTypeOrTypeParam.Parameter -> generateTodo()
            returnVariable != null -> {
                addToBody("return ${returnVariable.name}")
            }
            numericReturnType != null && numericVariable != null -> {
                addToBody("return ${CastOperator(context, 0, Variable(context, numericVariable), numericReturnType)}")
            }
            (returnType as KtTypeOrTypeParam.Type).type.isMarkedNullable -> {
                addToBody("return null")
            }
//            else -> generateTodo()
            // TODO: add generation from functions or properties
//            else -> {
//                val instance = ClassInstanceGenerator(file).generateRandomInstanceOfUserClass(returnType.type)?.first
//                    ?: return generateTodo()
//                addToBody("return ${instance.text}")
//            }
        }
    }
}