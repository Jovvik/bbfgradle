package com.stepanov.bbf.generator

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction

data class Context(
    val customClasses: MutableList<KtClass> = mutableListOf(),
    val customFunctions: MutableList<KtFunction> = mutableListOf(),
    var visibleVariables: MutableList<KtCallableDeclaration> = mutableListOf(),
    var visibleFunctions: MutableList<KtFunction> = mutableListOf()
) {
    val visibleNumericVariables: List<KtCallableDeclaration>
        get() = visibleVariables.filter { numericPrimitives.contains(it.typeReference?.text) }

    companion object {
        private val numericPrimitives = enumValues<PrimitiveType>()
                .map { it.typeName.asString() }
                .filter { !listOf("Boolean", "Char").contains(it) }
    }
}