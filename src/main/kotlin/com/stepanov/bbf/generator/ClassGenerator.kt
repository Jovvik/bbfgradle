package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
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
            when (Policy.classKindTable()) {
                Policy.ClassKind.DATA -> generateDataclass()
                Policy.ClassKind.ENUM -> generateEnum()
                Policy.ClassKind.INTERFACE -> generateInterface()
                Policy.ClassKind.REGULAR -> generateClass(classLimit)
            }
        }
        addUnimplementedProperties()
        addPropertiesForConstructor()
    }

    private fun generateInterface() {
        val cls = createClass("interface")
        val propertyGenerator = PropertyGenerator(context, cls)
        repeat(Policy.propertyLimit()) {
            propertyGenerator.generate(it)
        }
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
                val propertyType = context.customClasses.firstOrNull { it.name == propertyDescriptor.type.name!! }
                val propertyTypeName = propertyType?.getFullyQualifiedName(context, cls.typeParameters, false)?.first
                    ?: propertyDescriptor.type.name!!
                propertyGenerator.addConstructorArgument(
                    propertyDescriptor.name.asString(),
                    ClassOrBasicType(propertyTypeName, propertyType),
                    cls.typeParameters.firstOrNull { it.name == propertyDescriptor.type.name!! },
                    true,
                    propertyDescriptor.isVar
                )
            }
        }
    }

    private fun generateEnum() {
        val cls = createClass("enum class")
        repeat(Policy.enumValueLimit()) {
            cls.addPsiToBody(Factory.psiFactory.createEnumEntry("VALUE_$it,"))
        }
        saveClass(cls)
    }

    // TODO: generics
    private fun generateDataclass() {
        val cls = createClass("data class")
        val propertyGenerator = PropertyGenerator(context, cls)
        repeat(Policy.propertyLimit()) {
            // maybe properties in body?
            propertyGenerator.addConstructorArgument(
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

        // TODO: bounds
        val typeParameters = (0 until Policy.typeParameterLimit()).map {
            val paramName = indexString("T", context, it)
            Factory.psiFactory.createTypeParameter("${Policy.varianceTable().label} $paramName")
        }
        val inheritedClasses = Policy.inheritedClasses(context)
        val qualifiedInheritedClasses =
            inheritedClasses.map { klass ->
                val (resolved, chosenParameters) = klass.getFullyQualifiedName(context, typeParameters, false)
                Triple(resolved, chosenParameters, klass)
            }
        var cls = createClass(
            "class",
            classModifiers,
            typeParameters,
            qualifiedInheritedClasses
        )
        val propertyGenerator = PropertyGenerator(context, cls)
        typeParameters.forEach { cls.typeParameterList!!.addParameter(it) }
        propertiesToAdd[cls.name!!] = qualifiedInheritedClasses

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

    private fun addPropertiesForConstructor() {
        val ctx = PSICreator.analyze(file)!!
        println(file.text)
        RandomTypeGenerator.setFileAndContext(file, ctx)
        for (cls in file.getAllPSIChildrenOfType<KtClass>().filter { !it.isAbstract() }) {
            val propertyGenerator = PropertyGenerator(context, cls)
            if (cls.name!! !in propertiesToAdd) {
                continue
            }
            val inheritedClasses = propertiesToAdd[cls.name!!]!!
            for ((_, tps, inheritedClass) in inheritedClasses) {
//                val classDescriptor = file.getAllPSIChildrenOfType<KtClass>().first { it.name == inheritedClass.name }
//                    .getDeclarationDescriptorIncludingConstructors(ctx)
//                val replaced =
//                    (classDescriptor as LazyClassDescriptor).defaultType.replace(tps.map {
//                        RandomTypeGenerator.generateType(
//                            it.name
//                        )!!.asTypeProjection()
//                    })
//                println(replaced.memberScope)

                for (parameter in inheritedClass.primaryConstructorParameters) {
                    if (parameter.hasDefaultValue() && !Policy.provideArgumentWithDefaultValue()) {
                        continue
                    }
                    val typeName = parameter.typeReference!!.text
                    propertyGenerator.addConstructorArgument(
                        nameForConstructor(parameter),
                        ClassOrBasicType(typeName),
                        cls.typeParameters.firstOrNull { it.name!! == typeName }
                    )
                }
            }
        }
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

    private fun createClass(
        keyword: String,
        classModifiers: List<String> = emptyList(),
        typeParameters: List<KtTypeParameter> = emptyList(),
        inheritedClasses: List<Triple<String, List<ClassOrBasicType>, KtClass>> = emptyList()
    ): KtClass {
        val typeParameterBrackets = if (typeParameters.isEmpty()) "" else "<>"
        val inheritanceBlock =
            if (inheritedClasses.isEmpty()) {
                ""
            } else {
                " : " + inheritedClasses.joinToString(", ") {
                    it.first + it.third.getPrimaryConstructorParameterList()!!.parameters.joinToString(
                        ", ",
                        "(",
                        ")",
                        transform = ::nameForConstructor
                    )
                }
            }
        val classText =
            "${classModifiers.joinToString(" ")} $keyword Class${context.customClasses.size}$typeParameterBrackets() $inheritanceBlock {\n}"
//        println(classText)
        return Factory.psiFactory.createClass(classText)
    }

    private fun nameForConstructor(clsOrBasic: ClassOrBasicType) = nameForConstructor(clsOrBasic.name)

    private fun nameForConstructor(parameter: KtParameter) = nameForConstructor(parameter.name!!)

    private fun nameForConstructor(str: String): String {
        return "${str}_${context.customClasses.size}"
    }

    private fun saveClass(cls: KtClass) {
        context.customClasses.add(cls)
        file.addAtTheEnd(cls)
    }

    // TODO: make a wrapper
    // class name -> pair
    private val propertiesToAdd =
        mutableMapOf<String, List<Triple<String, List<ClassOrBasicType>, KtClass>>>()
}