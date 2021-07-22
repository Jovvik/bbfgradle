package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.Variance

class PropertyGenerator(val context: Context, val cls: KtClass) {
    fun generate(propertyIndex: Int) {
        val modifiers = mutableListOf<String>()
        if (cls.isInterface()) {
            modifiers.add("public")
        } else {
            modifiers.add(Policy.propertyVisibilityTable().toString())
        }
        if (cls.isInterface() || (modifiers.first() != "private" && cls.isAbstract() && Policy.isAbstractProperty())) {
            modifiers.add("abstract")
        }
        val type = Policy.chooseType(context, cls.typeParameters)
        val name = indexString("property", context, propertyIndex)
        val typeParameter = cls.typeParameters.firstOrNull { it.name == type.name }
        // abstract case is tmp until instance generator
        if (!cls.isInterface() && (!modifiers.contains("abstract") || typeParameter != null || type.hasTypeParameters || Policy.isDefinedInConstructor())) {
            addConstructorArgument(name, type, typeParameter)
        } else {
            cls.addPsiToBody(
                Factory.psiFactory.createProperty(
                    modifiers.joinToString(" "),
                    name,
                    type.name,
                    Policy.isVar(),
                    if (modifiers.contains("abstract")) null else Policy.randomConst(type, context)
                )
            )
        }
    }

    fun addConstructorArgument(
        name: String,
        type: ClassOrBasicType,
        typeParameter: KtTypeParameter?,
        isOverride: Boolean = false,
        forceVar: Boolean? = null
    ) {
        val parameterTokens = mutableListOf<String>()
        if (isOverride) {
            parameterTokens.add("override")
        }
        val isVal = when {
            forceVar != null -> !forceVar
            typeParameter?.variance == Variance.OUT_VARIANCE || !Policy.isVar() -> true
            else -> false
        }
        parameterTokens.addAll(
            listOf(
                if (isVal) "val" else "var",
                name,
                ":",
                type.name
            )
        )
        if (typeParameter == null && !type.hasTypeParameters && Policy.hasDefaultValue()) {
            parameterTokens.add("=")
            parameterTokens.add(Policy.randomConst(type, context))
        }
        cls.getPrimaryConstructorParameterList()!!
            .addParameter(Factory.psiFactory.createParameter(parameterTokens.joinToString(" ")))
    }
}