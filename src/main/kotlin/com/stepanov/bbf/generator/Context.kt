package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass

data class Context(val customClasses: MutableList<KtClass> = mutableListOf())