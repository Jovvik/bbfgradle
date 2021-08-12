package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.KotlinType

sealed class KtTypeOrTypeParam {
    class Type(val type: KotlinType) : KtTypeOrTypeParam() {
        override val name = type.toString()
    }

    class Parameter(val parameter: KtTypeParameter) : KtTypeOrTypeParam() {
        override val name = parameter.name!!
    }

    val hasTypeParameters: Boolean = true
    abstract val name: String
}