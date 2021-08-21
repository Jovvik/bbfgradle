package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.Variance

class PropertyGenerator(val context: Context, val containingClass: KtClass) {
    fun generate(propertyIndex: Int) {
        val visibility = if (containingClass.isInterface()) Policy.Visibility.PUBLIC else Policy.propertyVisibility()
        val isAbstract =
            containingClass.isInterface() || (visibility != Policy.Visibility.PRIVATE && containingClass.isAbstract() && Policy.isAbstractProperty())
        val type = Policy.chooseType(containingClass.typeParameters, Variance.INVARIANT, Variance.OUT_VARIANCE)
        val name = indexString("property", context, propertyIndex)
        // abstract case is tmp until instance generator
        if (!containingClass.isInterface() && (!isAbstract || type is KtTypeOrTypeParam.Parameter || Policy.isDefinedInConstructor())) {
            addConstructorArgument(name, type)
        } else {
            containingClass.addPsiToBody(
                Factory.psiFactory.createProperty(
                    getModifiers(visibility, isAbstract),
                    name,
                    type.name,
                    Policy.isVar(),
                    if (isAbstract) null else Policy.randomConst(
                        (type as KtTypeOrTypeParam.Type).type,
                        context
                    )
                )
            )
        }
    }

    fun addConstructorArgument(
        name: String,
        type: KtTypeOrTypeParam,
        isOverride: Boolean = false,
        forceVar: Boolean? = null,
        noVarVal: Boolean = false
    ) {
        val parameterTokens = mutableListOf<String>()
        if (isOverride) {
            parameterTokens.add("override")
        }
        val isVal = when {
            noVarVal -> null
            forceVar != null -> !forceVar
            (type as? KtTypeOrTypeParam.Parameter)?.parameter?.variance == Variance.OUT_VARIANCE -> true
            !Policy.isVar() -> true
            else -> false
        }
        if (isVal != null) {
            parameterTokens += if (isVal) "val" else "var"
        }
        parameterTokens += listOf(name, ":", type.name)
        if (!type.hasTypeParameters && type !is KtTypeOrTypeParam.Parameter && Policy.hasDefaultValue()) {
            parameterTokens.add("=")
            parameterTokens.add(Policy.randomConst((type as KtTypeOrTypeParam.Type).type, context))
        }
        val parameter = Factory.psiFactory.createParameter(parameterTokens.joinToString(" "))
        if (!containingClass.getPrimaryConstructorParameterList()!!.parameters.map { it.name }.contains(name)) {
            containingClass.getPrimaryConstructorParameterList()!!.addParameter(parameter)
        }
    }

    private fun getModifiers(visibility: Policy.Visibility, isAbstract: Boolean): String {
        val modifiers = mutableListOf(visibility.toString())
        if (isAbstract) {
            modifiers.add("abstract")
        }
        return modifiers.joinToString(" ")
    }
}