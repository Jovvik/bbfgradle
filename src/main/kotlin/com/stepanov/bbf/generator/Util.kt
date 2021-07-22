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
    depth: Int = 0,
    withTypeParameters: Boolean = true
): Pair<String, List<ClassOrBasicType>> {
    val containingClass = containingClass()
    val (resolvedThis, chosenTypeParameters) = Policy.resolveTypeParameters(this, context, typeParameterList, depth)
    val resolvedTotal = if (containingClass == null) {
        ""
    } else {
        if (isInner() && withConstructors) {
            Policy.randomConst(ClassOrBasicType(containingClass.name!!, containingClass), context)
        } else {
            containingClass.getFullyQualifiedName(
                context,
                typeParameterList,
                withConstructors,
                depth,
                isInner()
            ).first
        } + "."
    } + if (withTypeParameters) {
        resolvedThis.name
    } else {
        name!!
    }
    return Pair(resolvedTotal, chosenTypeParameters)
}

fun KtClass.isInheritableClass(): Boolean {
    return !isInterface() && !isInner() && (isSealed() || isInterface() || isAbstract() || hasModifier(KtTokens.OPEN_KEYWORD))
}

fun indexString(prefix: String, context: Context, vararg index: Int): String {
    return "${prefix}_${context.customClasses.size}_${index.joinToString("_")}"
}