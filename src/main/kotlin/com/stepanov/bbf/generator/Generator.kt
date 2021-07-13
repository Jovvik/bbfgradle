package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.Variance

class Generator {
    lateinit var context: Context
    lateinit var file: KtFile

    fun generate(): Project {
        context = Context()
        val project = Project.createFromCode("")
        file = project.files.first().psiFile as KtFile
        generateClasses()
        return project
    }

    private fun generateClasses() {
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
                indexString("property", it),
                Policy.chooseType(context, cls.typeParameters),
                cls,
                null
            )
        }
        saveClass(cls)
    }

    private fun saveClass(cls: KtClass) {
        context.customClasses.add(cls)
        file.addAtTheEnd(cls)
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
                Factory.psiFactory.createTypeParameter("${Policy.variance().label} ${indexString("T", it)}")
            )
        }
        repeat(Policy.propertyLimit()) {
            generateProperty(cls, it)
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

    private fun generateProperty(cls: KtClass, propertyIndex: Int) {
        val modifiers = mutableListOf(Policy.propertyVisibility())
        val type = Policy.chooseType(context, cls.typeParameters)
        val name = indexString("property", propertyIndex)
        val typeParameter = cls.typeParameters.firstOrNull { it.name == type.name }
        if (typeParameter != null || type.hasTypeParameters || Policy.isDefinedInConstructor()) {
            addConstructorArgument(name, type, cls, typeParameter)
        } else {
            cls.addPsiToBody(
                Factory.psiFactory.createProperty(
                    modifiers.joinToString(" "),
                    name,
                    type.name,
                    Policy.isVar(),
                    Policy.randomConst(type, context)
                )
            )
        }
    }

    private fun addConstructorArgument(
        name: String,
        type: ClassOrBasicType,
        cls: KtClass,
        typeParameter: KtTypeParameter?
    ) {
        val parameterTokens = mutableListOf(
            if (typeParameter?.variance == Variance.OUT_VARIANCE || !Policy.isVar()) "val" else "var",
            name, ":", type
        )
        if (typeParameter == null && !type.hasTypeParameters && Policy.hasDefaultValue()) {
            parameterTokens.add("=")
            parameterTokens.add(Policy.randomConst(type, context))
        }
        cls.getPrimaryConstructorParameterList()!!
            .addParameter(Factory.psiFactory.createParameter(parameterTokens.joinToString(" ")))
    }

    private fun indexString(prefix: String, index: Int): String {
        return "${prefix}_${context.customClasses.size}_${index}"
    }
}