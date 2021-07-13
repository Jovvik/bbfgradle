package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.containingClass

fun KtClass.getFullyQualifiedName(context: Context, withConstructors: Boolean): String {
    val containingClass = containingClass()
    return if (containingClass == null) {
        name!!
    } else {
        if (isInner() && withConstructors) {
            Policy.randomConst(ClassOrBasicType(containingClass.name!!, containingClass), context)
        } else {
            containingClass.getFullyQualifiedName(context, withConstructors)
        } + "." + name!!
    }
}