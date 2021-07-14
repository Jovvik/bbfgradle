package com.stepanov.bbf.generator

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isAbstract

fun KtClass.getFullyQualifiedName(
    context: Context,
    typeParameterList: List<KtTypeParameter>,
    withConstructors: Boolean,
    withTypeParameters: Boolean = true
): String {
    val containingClass = containingClass()
    val resolvedThis = Policy.resolveTypeParameters(this, context, typeParameterList).name
    return if (containingClass == null) {
        ""
    } else {
        if (isInner() && withConstructors) {
            Policy.randomConst(ClassOrBasicType(containingClass.name!!, containingClass), context)
        } else {
            containingClass.getFullyQualifiedName(context, typeParameterList, withConstructors, isInner())
        } + "."
    } + if (withTypeParameters) {
        resolvedThis
    } else {
        name!!
    }
}

fun KtClass.canBeInherited(): Boolean {
    return isSealed() || isInterface() || isAbstract() || hasModifier(KtTokens.OPEN_KEYWORD)
}

fun indexString(prefix: String, index: Int, context: Context): String {
    return "${prefix}_${context.customClasses.size}_${index}"
}