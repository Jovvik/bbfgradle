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
    depth: Int,
    withTypeParameters: Boolean = true
): String {
    val containingClass = containingClass()
    val resolvedThis = Policy.resolveTypeParameters(this, context, typeParameterList, depth).name
    return if (containingClass == null) {
        ""
    } else {
        if (isInner() && withConstructors) {
            Policy.randomConst(ClassOrBasicType(containingClass.name!!, containingClass), context)
        } else {
            containingClass.getFullyQualifiedName(context, typeParameterList, withConstructors, depth, isInner())
        } + "."
    } + if (withTypeParameters) {
        resolvedThis
    } else {
        name!!
    }
}

fun KtClass.isInheritableClass(): Boolean {
    return !isInterface() && (isSealed() || isInterface() || isAbstract() || hasModifier(KtTokens.OPEN_KEYWORD))
}

fun indexString(prefix: String, context: Context, vararg index: Int): String {
    return "${prefix}_${context.customClasses.size}_${index.joinToString("_")}"
}