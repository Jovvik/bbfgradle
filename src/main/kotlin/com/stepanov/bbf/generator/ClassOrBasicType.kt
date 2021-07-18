package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass

class ClassOrBasicType(val name: String, val cls: KtClass? = null) {
    val hasTypeParameters = cls?.typeParameterList != null

    override fun toString() = name
}