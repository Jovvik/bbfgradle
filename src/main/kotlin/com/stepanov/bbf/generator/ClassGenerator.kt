package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.name
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered

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
        addUnimplementedProperties()
    }

    private fun addUnimplementedProperties() {
        val bindingContext = PSICreator.analyze(file)!!
        for (cls in file.getAllPSIChildrenOfType<KtClass>().filter { !it.isAbstract() }) {
            val propertyGenerator = PropertyGenerator(context, cls)
            val classDescriptor = cls.getDeclarationDescriptorIncludingConstructors(bindingContext)!! as ClassDescriptor
            val descrs = classDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }
            // TODO: write it decently
            for (propertyDescriptor in descrs.filterIsInstance<PropertyDescriptor>()
                .filter { it.toString().contains("abstract") }) {
                propertyGenerator.addConstructorArgument(
                    propertyDescriptor.name.asString(),
                    ClassOrBasicType(propertyDescriptor.type.name!!),
                    cls.typeParameters.firstOrNull { it.name == propertyDescriptor.type.name!! },
                    true,
                    propertyDescriptor.isVar
                )
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
                indexString("property", context, it),
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
        val (classModifiers, isInner) = getClassModifiers(isInner, containingClass)

        val inheritedClasses = Policy.inheritedClasses(context)

        // TODO: bounds
        var typeParameters = mutableListOf<KtTypeParameter>()
        for (i in 0 until Policy.typeParameterLimit()) {
            val paramName = indexString("T", context, i)
            typeParameters.add(Factory.psiFactory.createTypeParameter("${Policy.variance().label} $paramName"))
        }
        // not actually needed?
//        inheritedClasses.forEach {
//            typeParameters.addAll(it.typeParameters)
//        }
//        typeParameters = typeParameters.distinctBy { it.text } as MutableList<KtTypeParameter>
        var cls = Factory.psiFactory.createClass(
            makeClassText(
                classModifiers,
                typeParameters,
                inheritedClasses
            )
        )
        val propertyGenerator = PropertyGenerator(context, cls)
        typeParameters.forEach { cls.typeParameterList!!.addParameter(it) }
        for (inheritedClass in inheritedClasses) {
            for (parameter in inheritedClass.primaryConstructorParameters) {
                if (parameter.hasDefaultValue() && !Policy.provideArgumentWithDefaultValue()) {
                    continue
                }
                val typeName = parameter.typeReference!!.text
                propertyGenerator.addConstructorArgument(
                    forConstructorName(parameter),
                    ClassOrBasicType(typeName),
                    typeParameters.firstOrNull { it.name!! == typeName }
                )
            }
        }

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

    // tmp
    private fun printList(col: Collection<KtTypeParameter>): String {
        return col.joinToString(", ") { it.text!! }
    }

    private fun getClassModifiers(
        isInner: Boolean,
        containingClass: KtClass?
    ): Pair<MutableList<String>, Boolean> {
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
        return Pair(classModifiers, isInner)
    }

    private fun makeClassText(
        classModifiers: MutableList<String>,
        typeParameters: List<KtTypeParameter>,
        inheritedClasses: List<KtClass>
    ): String {
        val typeParameterBrackets = if (typeParameters.isEmpty()) "" else "<>"
        val inheritanceBlock =
            if (inheritedClasses.isEmpty()) {
                ""
            } else {
                " : " + inheritedClasses.joinToString(", ") {
                    it.getFullyQualifiedName(context, typeParameters, false, 0) +
                            it.getPrimaryConstructorParameterList()!!.parameters.joinToString(
                                ", ",
                                "(",
                                ")",
                                transform = ::forConstructorName
                            )
                }
            }
        return "${classModifiers.joinToString(" ")} class Class${context.customClasses.size}$typeParameterBrackets() $inheritanceBlock {\n}"
    }

    private fun forConstructorName(parameter: KtParameter): String {
        return "${parameter.name!!}_${context.customClasses.size}"
    }

    private fun saveClass(cls: KtClass) {
        context.customClasses.add(cls)
        file.addAtTheEnd(cls)
    }
}