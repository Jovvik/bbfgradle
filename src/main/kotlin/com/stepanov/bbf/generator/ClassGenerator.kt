package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.Variance

class ClassGenerator(val context: Context, val file: KtFile) {
    fun generate() {
        val classLimit = Policy.classLimit()
        while (classLimit > context.customClasses.size) {
            when {
                Policy.isEnum() -> generateEnum()
                Policy.isDataclass() -> generateDataclass()
                else -> generateClass(classLimit)
            }
        }
    }

    private fun generateEnum() {
        val cls = Factory.psiFactory.createClass("enum class Class${context.customClasses.size} {\n}")
        repeat(Policy.enumValueLimit()) {
            cls.addPsiToBody(Factory.psiFactory.createEnumEntry("VALUE_$it,"))
        }
        saveClass(cls)
    }

    // TODO: generics
    private fun generateDataclass() {
        val cls = Factory.psiFactory.createClass("data class Class${context.customClasses.size}()")
        repeat(Policy.propertyLimit()) {
            // maybe properties in body?
            addConstructorArgument(
                indexString("property", it, context),
                Policy.chooseType(context, cls.typeParameters),
                cls,
                null
            )
        }
        saveClass(cls)
    }

    // TODO: abstract class, inheritance
    private fun generateClass(
        classLimit: Int,
        containingClass: KtClass? = null,
        depth: Int = 0,
        isInner: Boolean = false
    ) {
        val classModifiers = mutableListOf<String>()
        // intentional shadowing
        val isInner = isInner || (containingClass != null && Policy.isInner())
        if (isInner) {
            classModifiers.add("inner")
        }
        if (Policy.isSealed()) {
            classModifiers.add("sealed")
        }
        if (Policy.isOpen()) {
            classModifiers.add("open")
        }
        val typeParameterLimit = Policy.typeParameterLimit()
        var cls = Factory.psiFactory.createClass(
            "${classModifiers.joinToString(" ")} class Class${context.customClasses.size}${if (typeParameterLimit > 0) "<>" else ""}(){\n}"
        )
        // TODO: bounds
        repeat(typeParameterLimit) {
            cls.typeParameterList!!.addParameter(
                Factory.psiFactory.createTypeParameter("${Policy.variance().label} ${indexString("T", it, context)}")
            )
        }
        val propertyGenerator = PropertyGenerator(context, cls, this)
        repeat(Policy.propertyLimit()) {
            propertyGenerator.generate(it)
        }
        if (containingClass != null) {
            cls = containingClass.addPsiToBody(cls) as KtClass
        }
        context.customClasses.add(cls)
        repeat(Policy.nestedClassLimit()) {
            if (classLimit > context.customClasses.size && depth < Policy.maxNestedClassDepth) {
                generateClass(classLimit, cls, depth + 1, isInner)
            }
        }
        if (containingClass == null) {
            file.addAtTheEnd(cls)
        }
    }

    fun addConstructorArgument(
        name: String,
        type: ClassOrBasicType,
        cls: KtClass,
        typeParameter: KtTypeParameter?
    ) {
        val parameterTokens = mutableListOf(
            if (typeParameter?.variance == Variance.OUT_VARIANCE || !Policy.isVar()) "val" else "var",
            name, ":", type.name
        )
        if (typeParameter == null && !type.hasTypeParameters && Policy.hasDefaultValue()) {
            parameterTokens.add("=")
            parameterTokens.add(Policy.randomConst(type, context))
        }
        cls.getPrimaryConstructorParameterList()!!
            .addParameter(Factory.psiFactory.createParameter(parameterTokens.joinToString(" ")))
    }

    private fun saveClass(cls: KtClass) {
        context.customClasses.add(cls)
        file.addAtTheEnd(cls)
    }
}