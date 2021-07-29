package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.Variance

class FunctionGenerator(val context: Context, val file: KtFile, val containingClass: KtClass? = null) {
    fun generate(index: Int) {
        val typeParameters = (0 until Policy.typeParameterLimit()).map {
            Factory.psiFactory.createTypeParameter("T_$it")
        }
        val isInfix = containingClass != null && Policy.isInfixFunction()
        val isAbstract = containingClass != null && containingClass.isAbstract() && Policy.isAbstractFunction()
        val fn = createFunction(isInfix, isAbstract, typeParameters, index)
        generateBody(fn)
        typeParameters.forEach { fn.typeParameterList!!.addParameter(it) }
        val parameterCount = if (isInfix) 1 else Policy.functionParameterLimit()
        repeat(parameterCount) {
            // TODO: when default parameter values are implemented,
            // pass `isInfixFunction` since it prohibits default parameter values
            generateParameter(typeParameters, fn, it)
        }
        if (containingClass != null) {
            containingClass.addPsiToBody(fn)
        } else {
            file.addAtTheEnd(fn)
            context.customFunctions.add(fn)
        }
    }

    // TODO: expressions
    private fun generateBody(fn: KtNamedFunction) {
        if (!fn.hasBody()) {
            return
        }
        val bodyExpression = fn.bodyExpression!!
        bodyExpression.addBefore(Factory.psiFactory.createExpression("TODO()"), bodyExpression.lastChild)
    }

    private fun createFunction(
        isInfix: Boolean,
        isAbstract: Boolean,
        typeParameters: List<KtTypeParameter>,
        index: Int
    ): KtNamedFunction {
        val modifiers = getModifiers(isInfix, isAbstract)
        val returnType = chooseType(typeParameters, false)
        val body = if (isAbstract) "" else "{\n}"
        return Factory.psiFactory.createFunction(
            "$modifiers fun ${if (typeParameters.isEmpty()) "" else "<> "}${getName(index)}(): ${returnType.name} $body"
        )
    }

    private fun getModifiers(isInfix: Boolean, isAbstract: Boolean): String {
        val modifiers = mutableListOf<String>()
        if (isAbstract) {
            modifiers.add("abstract")
        }
        if (isInfix) {
            modifiers.add("infix")
        }
        if (!isAbstract && Policy.isInlineFunction()) {
            modifiers.add("inline")
        }
        return modifiers.joinToString(" ")
    }

    private fun generateParameter(
        typeParameters: List<KtTypeParameter>,
        fn: KtNamedFunction,
        index: Int,
        chosenType: KtTypeOrTypeParam = chooseType(typeParameters, true)
    ) {
        fn.valueParameterList!!.addParameter(Factory.psiFactory.createParameter("param_$index : ${chosenType.name}"))
    }

    private fun chooseType(typeParameters: List<KtTypeParameter>, isValueArgument: Boolean): KtTypeOrTypeParam {
        return Policy.chooseType(
            typeParameters + containingClass?.typeParameters.orEmpty(),
            Variance.INVARIANT,
            if (isValueArgument) Variance.IN_VARIANCE else Variance.OUT_VARIANCE
        )
    }

    private fun getName(index: Int): String {
        return if (containingClass == null) {
            "f_${context.customFunctions.size}"
        } else {
            "f_${context.customClasses.size}_$index"
        }
    }
}