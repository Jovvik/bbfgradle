package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction

data class Context(
    val customClasses: MutableList<KtClass> = mutableListOf(),
    val customFunctions: MutableList<KtFunction> = mutableListOf()
)