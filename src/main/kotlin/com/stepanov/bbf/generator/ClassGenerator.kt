package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

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
            PropertyGenerator(context, cls).addConstructorArgument(
                indexString("property", it, context),
                Policy.chooseType(context, cls.typeParameters),
                null
            )
        }
        saveClass(cls)
    }

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
        when {
            Policy.isSealed() -> classModifiers.add("sealed")
            Policy.isOpen() -> classModifiers.add("open")
            Policy.isAbstract() -> classModifiers.add("abstract")
        }

        val typeParameterLimit = Policy.typeParameterLimit()
        var cls = Factory.psiFactory.createClass(
            makeClassText(
                classModifiers,
                typeParameterLimit,
                Policy.inheritedClasses(context)
            )
        )

        // TODO: bounds
        repeat(typeParameterLimit) {
            cls.typeParameterList!!.addParameter(
                Factory.psiFactory.createTypeParameter("${Policy.variance().label} ${indexString("T", it, context)}")
            )
        }
        val propertyGenerator = PropertyGenerator(context, cls)
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

    private fun makeClassText(
        classModifiers: MutableList<String>,
        typeParameterLimit: Int,
        inheritedClasses: List<KtClass>
    ): String {
        val typeParameterBrackets = if (typeParameterLimit == 0) "" else "<>"
        val inheritanceBlock =
            if (inheritedClasses.isEmpty()) "" else " : " + inheritedClasses.joinToString(", ") { it.name!! }
        return "${classModifiers.joinToString(" ")} class Class${context.customClasses.size}$typeParameterBrackets$inheritanceBlock(){\n}"
    }

    private fun saveClass(cls: KtClass) {
        context.customClasses.add(cls)
        file.addAtTheEnd(cls)
    }
}