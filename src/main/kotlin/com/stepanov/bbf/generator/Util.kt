package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.KotlinType

fun KtClass.getFullyQualifiedName(
    withConstructors: Boolean,
    depth: Int = 0,
    withTypeParameters: Boolean = true
): Pair<String, List<KotlinType>> {
    val containingClass = containingClass()
    val (resolvedThis, chosenTypeParameters) = Policy.resolveTypeParameters(this)
    val resolvedTotal = if (containingClass == null) {
        ""
    } else {
        if (isInner() && withConstructors) {
            throw IllegalArgumentException("Cannot generate random constants yet")
        } else {
            containingClass.getFullyQualifiedName(
                withConstructors,
                depth,
                isInner()
            ).first
        } + "."
    } + if (withTypeParameters) {
        resolvedThis
    } else {
        name!!
    }
    return Pair(resolvedTotal, chosenTypeParameters)
}

fun KtClassOrObject.isOpen() = hasModifier(KtTokens.OPEN_KEYWORD)

fun KtClass.isInheritableClass(): Boolean {
    return !isInterface() && !isInner() && (isSealed() || isAbstract() || isOpen())
}

/**
 * Returns a list of all containing classes whose members are reachable from this class,
 * including `this`
 */
fun KtClass.getReachableContainingClasses(): List<KtClass> {
    val containingClass = containingClass()
    if (!isInner() || containingClass == null) {
        return listOf(this)
    }
    return containingClass.getReachableContainingClasses() + this
}

fun indexString(prefix: String, context: Context, vararg index: Int): String {
    return "${prefix}_${context.customClasses.size}_${index.joinToString("_")}"
}

val RandomTypeGenerator.forbiddenTypes: List<String>
    get() = listOf(
        "[",
    )

fun RandomTypeGenerator.generateRandomStandardTypeWithCtx(
    upperBounds: KotlinType? = null,
    depth: Int = 0
): KotlinType {
    // is this really the easiest way to produce a sequence of nullables?
    return sequence {
        while (true) {
            yield(generateRandomTypeWithCtx(upperBounds, depth))
        }
    }
            .take(20)
            .filterNotNull()
            .firstOrNull { type ->
                type.toString().let { typename ->
                    forbiddenTypes.all { !typename.contains(it) }
                }
            } ?: upperBounds ?: generateType(generatePrimitive()) ?: throw Exception("Couldn't generate the type")
}