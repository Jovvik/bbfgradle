package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.name
import com.stepanov.bbf.reduktor.parser.PSICreator
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

class ClassGenerator(val context: Context, val file: KtFile) {
    fun generate() {
        val classLimit = Policy.classLimit()
        while (classLimit > context.customClasses.size) {
            RandomTypeGenerator.setFileAndContext(file, PSICreator.analyze(file)!!)
            when (Policy.classKind()) {
                Policy.ClassKind.DATA -> generateDataclass()
                Policy.ClassKind.ENUM -> generateEnum()
                Policy.ClassKind.INTERFACE -> generateInterface()
                Policy.ClassKind.REGULAR -> generateClass(classLimit)
            }
        }
        addUnimplemented()
        RandomTypeGenerator.setFileAndContext(file, PSICreator.analyze(file)!!)
        val functionGenerator = FunctionGenerator(context, file)
        repeat(Policy.freeFunctionLimit()) {
            functionGenerator.generate(it)
        }
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

    private fun generateEnum() {
        val cls = createClass("enum class")
        repeat(Policy.enumValueLimit()) {
            cls.addPsiToBody(Factory.psiFactory.createEnumEntry("VALUE_$it,"))
        }
        saveClass(cls)
    }

    private fun generateDataclass() {
        val cls = createClass("data class")
        val propertyGenerator = PropertyGenerator(context, cls)
        repeat(Policy.propertyLimit()) {
            propertyGenerator.addConstructorArgument(
                indexString("property", context, it),
                Policy.chooseType(cls.typeParameters, Variance.INVARIANT, Variance.OUT_VARIANCE)
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

        val typeParameters = (0 until Policy.typeParameterLimit()).map {
            val paramName = indexString("T", context, it)
            val parameter = Factory.psiFactory.createTypeParameter("${Policy.variance().label} $paramName")
            if (Policy.useBound()) {
                // Upper bound of a type parameter cannot be an array
                while (parameter.extendsBound?.text?.startsWith("Array<") != false) {
                    parameter.extendsBound =
                        Factory.psiFactory.createType(
                            RandomTypeGenerator.generateRandomStandardTypeWithCtx()!!.toString()
                        )
                }
            }
            parameter
        }
        val inheritedClasses = Policy.inheritedClasses(context)
        val qualifiedInheritedClasses =
            inheritedClasses.map { klass ->
                val (resolved, chosenParameters) = klass.getFullyQualifiedName(false)
                InheritedClass(resolved, chosenParameters, klass)
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

    // TODO: functions
    private fun addUnimplemented() {
        for (cls in file.getAllPSIChildrenOfType<KtClass>()
                .filter { it.name?.startsWith(CLASS_PREFIX) ?: false }
                .sortedBy { it.name!!.substring(CLASS_PREFIX.length).toInt() }) {
            if (cls.name!! !in propertiesToAdd) {
                continue
            }
            val ctx = PSICreator.analyze(file)!! // re-analyzing each time since signatures may change
            RandomTypeGenerator.setFileAndContext(file, ctx)
            val propertyGenerator = PropertyGenerator(context, cls)
            val functionGenerator = FunctionGenerator(context, file, cls)
            val implementedFunctions = mutableSetOf<String>()
            val (classIndex, inheritedClasses) = propertiesToAdd[cls.name!!]!!
            for ((resolvedName, typeParameters, inheritedClass) in inheritedClasses) {
                val inheritedClassDescriptor =
                    file.getAllPSIChildrenOfType<KtClass>().first { it.name == inheritedClass.name }
                            .getDeclarationDescriptorIncludingConstructors(ctx)!! as LazyClassDescriptor
                val members = inheritedClassDescriptor.getMemberScope(typeParameters.map { it.asTypeProjection() })
                        .getDescriptorsFiltered { true }
                addConstructorParameters(
                    cls,
                    inheritedClassDescriptor,
                    resolvedName,
                    classIndex,
                    propertyGenerator,
                    typeParameters
                )
                addProperties(cls, members.filterIsInstance<PropertyDescriptor>(), propertyGenerator)
                addFunctions(
                    cls,
                    members.filterIsInstance<FunctionDescriptor>()
                            .filter { !implementedFunctions.contains(it.name.asString()) },
                    functionGenerator
                )
                implementedFunctions.addAll(members.filterIsInstance<FunctionDescriptor>().map { it.name.asString() })
            }
        }
    }

    private fun addProperties(
        cls: KtClass,
        properties: Collection<PropertyDescriptor>,
        propertyGenerator: PropertyGenerator
    ) {
        if (cls.isAbstract()) {
            return
        }
        for (propertyDescriptor in properties.filter { it.toString().contains("abstract") }) {
            propertyGenerator.addConstructorArgument(
                propertyDescriptor.name.asString(),
                KtTypeOrTypeParam.Type(propertyDescriptor.type),
                true,
                propertyDescriptor.isVar
            )
        }
    }

    private fun addFunctions(
        cls: KtClass,
        functions: Collection<FunctionDescriptor>,
        functionGenerator: FunctionGenerator
    ) {
        if (cls.isAbstract()) {
            return
        }
        functions.filter { it.modality == Modality.ABSTRACT }
                .forEach(functionGenerator::generateOverride)
    }

    private fun addConstructorParameters(
        cls: KtClass,
        inheritedClass: LazyClassDescriptor,
        resolvedName: String,
        classIndex: Int,
        propertyGenerator: PropertyGenerator,
        chosenTypeParameters: List<KotlinType>
    ) {
        if (inheritedClass.constructors.isEmpty()) {
            return
        }
//        val primaryConstructor = inheritedClass.substitute(
//            TypeSubstitutor.create(
//                inheritedClass.declaredTypeParameters
//                        .withIndex()
//                        .associateBy({ it.value.typeConstructor }) {
//                            chosenTypeParameters[it.index].asTypeProjection()
//                        }
//            )
//        ).constructors.first()
        for (parameter in inheritedClass.constructors.first().valueParameters) {
            val name = "${parameter.name}_${classIndex}"
            val type = if (parameter.type.isTypeParameter()) {
                val idx = inheritedClass.declaredTypeParameters.withIndex()
                        .first { it.value.name.asString() == parameter.type.name!! }.index
                chosenTypeParameters[idx]
            } else {
                parameter.type
            }
            propertyGenerator.addConstructorArgument(
                name,
                KtTypeOrTypeParam.Type(type)
            )
            val argList = cls.superTypeListEntries.first { it.typeReference!!.text == resolvedName }.children[1]
            (argList as KtValueArgumentList).addArgument(Factory.psiFactory.createArgument(name))
        }
    }

    private fun getClassModifiers(
        isInner: Boolean,
        containingClass: KtClass?
    ): Pair<List<String>, Boolean> {
        val classModifiers = mutableListOf<String>()
        // intentional shadowing
        val isInner = isInner || (containingClass != null && Policy.isInner())
        if (isInner) {
            classModifiers.add("inner")
        }
        when {
            !isInner && Policy.isSealed() -> classModifiers.add("sealed")
            Policy.isOpen() -> classModifiers.add("open")
            Policy.isAbstractClass() -> classModifiers.add("abstract")
        }
        return Pair(classModifiers, isInner)
    }

    private fun createClass(
        keyword: String,
        classModifiers: List<String> = emptyList(),
        typeParameters: List<KtTypeParameter> = emptyList(),
        inheritedClasses: List<InheritedClass> = emptyList(),
        withPrimaryConstructor: Boolean = true
    ): KtClass {
        val typeParameterBrackets = if (typeParameters.isEmpty()) "" else "<>"
        val inheritanceBlock =
            if (inheritedClasses.isEmpty()) {
                ""
            } else {
                " : " + inheritedClasses.joinToString(", ") {
                    it.resolvedName + if (it.cls.getPrimaryConstructorParameterList()?.parameters == null) "" else "()"
                }
            }
        val primaryConstructor = if (withPrimaryConstructor) "()" else ""
        val classText =
            "${classModifiers.joinToString(" ")} $keyword $CLASS_PREFIX${context.customClasses.size}$typeParameterBrackets$primaryConstructor$inheritanceBlock {\n}"
        return Factory.psiFactory.createClass(classText)
    }

    private fun addFunctions(cls: KtClass) {
        val functionGenerator = FunctionGenerator(context, file, cls)
        repeat(Policy.functionLimit()) {
            functionGenerator.generate(it)
        }
    }

    private fun saveClass(cls: KtClass) {
        context.customClasses.add(cls)
        file.addAtTheEnd(cls)
    }

    private val propertiesToAdd =
        mutableMapOf<String, Pair<Int, List<InheritedClass>>>()

    companion object {
        private const val CLASS_PREFIX = "Class"
    }
}