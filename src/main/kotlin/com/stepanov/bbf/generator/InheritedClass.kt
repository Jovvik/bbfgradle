package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.types.KotlinType

data class InheritedClass(
    val resolvedName: String,
    val typeParameters: List<KotlinType>,
    val cls: KtClass
)
