package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClass

fun KtClass.getFullyQualifiedName(
    context: Context,
    typeParameterList: List<KtTypeParameter>,
    withConstructors: Boolean
): String {
    val containingClass = containingClass()
    val resolvedThis = Policy.resolveTypeParameters(this, context, typeParameterList).name
    return if (containingClass == null) {
        resolvedThis
    } else {
        if (isInner() && withConstructors) {
            Policy.randomConst(ClassOrBasicType(containingClass.name!!, containingClass), context)
        } else {
            containingClass.getFullyQualifiedName(context, typeParameterList, withConstructors)
        } + "." + resolvedThis
    }
}

fun indexString(prefix: String, index: Int, context: Context): String {
    return "${prefix}_${context.customClasses.size}_${index}"
}