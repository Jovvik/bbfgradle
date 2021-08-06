package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.Variance

class FunctionGenerator(val context: Context, val file: KtFile, val containingClass: KtClass? = null) {
    fun generate(index: Int) {
        val typeParameters = (0 until Policy.typeParameterLimit()).map {
            Factory.psiFactory.createTypeParameter("T_$it")
        }
        val isInfix = containingClass != null && Policy.isInfixFunction()
        val isAbstract =
            containingClass != null && (containingClass.isInterface() || (containingClass.isAbstract() && Policy.isAbstractFunction()))
        val parameterCount = if (isInfix) 1 else Policy.functionParameterLimit()
        val valueParameters = (0 until parameterCount).map { generateParameter(typeParameters, it) }
        generate(
            getName(index),
            typeParameters,
            valueParameters,
            isInfix,
            isAbstract,
            false
        )
    }

    fun generate(descriptor: FunctionDescriptor) = generate(
        descriptor.name.asString(),
        descriptor.typeParameters.map { Factory.psiFactory.createTypeParameter(it.name.asString()) },
        descriptor.valueParameters.map { Factory.psiFactory.createParameter("${it.name} : ${it.type}") },
        descriptor.isInfix,
        isAbstract = false,
        isOverride = true,
        returnType = if (descriptor.returnType == null) null else KtTypeOrTypeParam.Type(descriptor.returnType!!)
    )

    private fun generate(
        name: String,
        typeParameters: List<KtTypeParameter>,
        valueParameters: List<KtParameter>,
        isInfix: Boolean,
        isAbstract: Boolean,
        isOverride: Boolean,
        returnType: KtTypeOrTypeParam? = chooseType(typeParameters, false),
    ) {
        val fn = createFunction(isInfix, isAbstract, isOverride, typeParameters, name, returnType)
        typeParameters.forEach { fn.typeParameterList!!.addParameter(it) }
        valueParameters.forEach { fn.valueParameterList!!.addParameter(it) }
        if (fn.hasBody()) {
            ExpressionGenerator(fn.bodyExpression!!).generate()
        }
        if (containingClass != null) {
            containingClass.addPsiToBody(fn)
        } else {
            file.addAtTheEnd(fn)
            context.customFunctions.add(fn)
        }
    }

    private fun createFunction(
        isInfix: Boolean,
        isAbstract: Boolean,
        isOverride: Boolean,
        typeParameters: List<KtTypeParameter>,
        name: String,
        returnType: KtTypeOrTypeParam?
    ): KtNamedFunction {
        val modifiers = getModifiers(isInfix, isAbstract, isOverride)
        val body = if (isAbstract) "" else "{\n}"
        return Factory.psiFactory.createFunction(
            "$modifiers fun ${if (typeParameters.isEmpty()) "" else "<> "}$name(): ${returnType?.name.orEmpty()} $body"
        )
    }

    private fun getModifiers(isInfix: Boolean, isAbstract: Boolean, isOverride: Boolean): String {
        val modifiers = mutableListOf<String>()
        if (isAbstract) {
            modifiers.add("abstract")
        }
        if (isInfix) {
            modifiers.add("infix")
        }
        if (isOverride) {
            modifiers.add("override")
        }
        if (!isAbstract && Policy.isInlineFunction()) {
            modifiers.add("inline")
        }
        return modifiers.joinToString(" ")
    }

    private fun generateParameter(
        typeParameters: List<KtTypeParameter>,
        index: Int,
        chosenType: KtTypeOrTypeParam = chooseType(typeParameters, true)
    ): KtParameter {
        return Factory.psiFactory.createParameter("param_$index : ${chosenType.name}")
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
            indexString("f", context, index)
        }
    }
}