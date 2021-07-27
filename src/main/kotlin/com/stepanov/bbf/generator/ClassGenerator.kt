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
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.replace
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

class ClassGenerator(val context: Context, val file: KtFile) {
    fun generate() {
        val classLimit = Policy.classLimit()
        while (classLimit > context.customClasses.size) {
            RandomTypeGenerator.setFileAndContext(file, PSICreator.analyze(file)!!)
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
        val cls = createClass("interface", withPrimaryConstructor = false)
        val propertyGenerator = PropertyGenerator(context, cls)
        repeat(Policy.propertyLimit()) {
            propertyGenerator.generate(it)
        }
        addFunctions(cls)
        saveClass(cls)
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
                    KtTypeOrTypeParam.Type(propertyDescriptor.type),
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
                Policy.chooseType(cls.typeParameters),
                null
            )
        }
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
                val (resolved, chosenParameters) = klass.getFullyQualifiedName(false)
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
        propertiesToAdd[cls.name!!] = Pair(context.customClasses.size, qualifiedInheritedClasses)

        repeat(Policy.propertyLimit()) {
            propertyGenerator.generate(it)
        }
        addFunctions(cls)
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
        for (cls in file.getAllPSIChildrenOfType<KtClass>().sortedBy { it.name }) {
            val ctx = PSICreator.analyze(file)!! // re-analyzing each time since signatures may change
            RandomTypeGenerator.setFileAndContext(file, ctx)
            val propertyGenerator = PropertyGenerator(context, cls)
            if (cls.name!! !in propertiesToAdd) {
                continue
            }
            val (classIndex, inheritedClasses) = propertiesToAdd[cls.name!!]!!
            for ((resolvedName, tps, inheritedClass) in inheritedClasses) {
                val classDescriptor =
                    file.getAllPSIChildrenOfType<KtClass>().first { it.name == inheritedClass.name }
                        .getDeclarationDescriptorIncludingConstructors(ctx)!!
                val replaced =
                    (classDescriptor as LazyClassDescriptor).defaultType.replace(tps.map { it.asTypeProjection() })
                val constructors = (replaced.constructor.declarationDescriptor as LazyClassDescriptor).constructors
                if (constructors.isEmpty()) {
                    continue
                }
                for (parameter in constructors.first().valueParameters) {
                    propertyGenerator.addConstructorArgument(
                        "${parameter.name}_${classIndex}",
                        KtTypeOrTypeParam.Type(parameter.type),
                        null // why?
                    )
                    val superTypeEntry = cls.superTypeListEntries.first { it.typeReference!!.text == resolvedName }
                    println(superTypeEntry.children[1] as KtValueArgumentList)
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
            !isInner && Policy.isSealed() -> classModifiers.add("sealed")
            Policy.isOpen() -> classModifiers.add("open")
            Policy.isAbstract() -> classModifiers.add("abstract")
        }
        return Pair(classModifiers, isInner)
    }

    private fun createClass(
        keyword: String,
        classModifiers: List<String> = emptyList(),
        typeParameters: List<KtTypeParameter> = emptyList(),
        inheritedClasses: List<Triple<String, List<KotlinType>, KtClass>> = emptyList(),
        withPrimaryConstructor: Boolean = true
    ): KtClass {
        val typeParameterBrackets = if (typeParameters.isEmpty()) "" else "<>"
        val inheritanceBlock =
            if (inheritedClasses.isEmpty()) {
                ""
            } else {
                " : " + inheritedClasses.joinToString(", ") {
                    it.first + it.third.getPrimaryConstructorParameterList()?.parameters?.joinToString(
                        ", ",
                        "(",
                        ")",
                        transform = ::nameForConstructor
                    ).orEmpty()
                }
            }
        val primaryConstructor = if (withPrimaryConstructor) "()" else ""
        val classText =
            "${classModifiers.joinToString(" ")} $keyword Class${context.customClasses.size}$typeParameterBrackets$primaryConstructor$inheritanceBlock {\n}"
        return Factory.psiFactory.createClass(classText)
    }

    private fun addFunctions(cls: KtClass) {
        val functionGenerator = FunctionGenerator(context, file, cls)
        repeat(Policy.functionLimit()) {
            functionGenerator.generate(it)
        }
    }

    private fun nameForConstructor(parameter: KtParameter): String {
        return "${parameter.name!!}_${context.customClasses.size}"
    }

    private fun saveClass(cls: KtClass) {
        context.customClasses.add(cls)
        file.addAtTheEnd(cls)
    }

    // TODO: make a wrapper
    private val propertiesToAdd =
        mutableMapOf<String, Pair<Int, List<Triple<String, List<KotlinType>, KtClass>>>>()
}