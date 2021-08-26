package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.types.Variance

class FunctionGenerator(
    private val context: Context,
    private val file: KtFile,
    private val containingClass: KtClass? = null
) {
    private inner class Flags(val infix: Boolean, val abstract: Boolean, val override: Boolean) {
        /*
        The last condition is due to this example:
        ```
        abstract class A {
            abstract fun f(): Int
        }
        open class B : A() {
            override inline fun f(): Int = TODO()
        }
        ```
        `open` matters.
         */
        val inline =
            !abstract && Policy.isInlineFunction() && !(containingClass?.isOpen() == true || containingClass?.isSealed() == true && override)
    }

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
            Flags(isInfix, isAbstract, override = false)
        )
    }

    fun generateOverride(descriptor: FunctionDescriptor) = generate(
        descriptor.name.asString(),
        descriptor.typeParameters.map { Factory.psiFactory.createTypeParameter(it.name.asString()) },
        descriptor.valueParameters.map { Factory.psiFactory.createParameter("${it.name} : ${it.type}") },
        Flags(descriptor.isInfix, abstract = false, override = true),
        returnType = if (descriptor.returnType == null) null else KtTypeOrTypeParam.Type(descriptor.returnType!!)
    )

    private fun generate(
        name: String,
        typeParameters: List<KtTypeParameter>,
        valueParameters: List<KtParameter>,
        flags: Flags,
        returnType: KtTypeOrTypeParam? = chooseType(typeParameters, false),
    ) {
        val fn = createFunction(flags, typeParameters, name, returnType)
        typeParameters.forEach { fn.typeParameterList!!.addParameter(it) }
        valueParameters.forEach { fn.valueParameterList!!.addParameter(it) }
        if (fn.hasBody()) {
            val publicModifier = Factory.psiFactory.createClass("class A(public a: Int)").primaryConstructorParameters
                    .first().visibilityModifierType()
            context.visibleVariables = valueParameters.toMutableList()
            context.visibleFunctions = context.customFunctions
            containingClass?.getReachableContainingClasses()
                    .orEmpty()
                    .forEach { cls ->
                        context.visibleVariables += (cls.getProperties() + cls.primaryConstructorParameters.filter { it.hasValOrVar() })
                                .filterNot { flags.inline && it.visibilityModifierType() != publicModifier } // can't use non-public members in inline function
                        context.visibleFunctions += cls.declarations.filterIsInstance<KtFunction>()
                    }
            val bodyGenerator = BodyGenerator(fn.bodyExpression!!, context, file, returnType, fn)
            bodyGenerator.generate()
        }
        if (containingClass != null) {
            containingClass.addPsiToBody(fn)
        } else {
            file.addAtTheEnd(fn)
            context.customFunctions.add(fn)
        }
    }

    private fun createFunction(
        flags: Flags,
        typeParameters: List<KtTypeParameter>,
        name: String,
        returnType: KtTypeOrTypeParam?
    ): KtNamedFunction {
        val modifiers = getModifiers(flags)
        val body = if (flags.abstract) "" else "{\n}"
        return Factory.psiFactory.createFunction(
            "$modifiers fun ${if (typeParameters.isEmpty()) "" else "<> "}$name(): ${returnType?.name.orEmpty()} $body"
        )
    }

    private fun getModifiers(flags: Flags): String {
        val modifiers = mutableListOf<String>()
        if (flags.abstract) {
            modifiers.add("abstract")
        }
        if (flags.infix) {
            modifiers.add("infix")
        }
        if (flags.override) {
            modifiers.add("override")
        }
        if (flags.inline) {
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