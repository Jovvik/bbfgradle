package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.Variance
import java.lang.Integer.min
import kotlin.random.Random

object Policy {

    // utils

    private fun uniformDistribution(min: Int, max: Int): Int {
        return Random.nextInt(min, max)
    }

    private fun bernoulliDistribution(p: Double): Boolean {
        return Random.nextDouble() < p
    }

    // hard limits

    const val maxNestedClassDepth = 3

    const val maxTypeParameterDepth = 2

    // soft limits

    fun classLimit() = 20

    fun enumValueLimit() = uniformDistribution(1, 10)

    fun functionParameterLimit() = uniformDistribution(0, 3)

    fun functionLimit() = uniformDistribution(0, 3)

    fun nestedClassLimit() = uniformDistribution(1, 3)

    fun propertyLimit() = uniformDistribution(5, 10)

    // tmp
    fun typeParameterLimit() = 0

    // stuff

    fun isAbstract() = bernoulliDistribution(0.5)

    fun isInner() = bernoulliDistribution(0.3)

    // tmp
    fun isOpen() = bernoulliDistribution(0.0)

    // TODO
    fun isSealed() = false

    // tmp until instance generator
    fun isAbstractProperty() = bernoulliDistribution(1.0)

    fun isDefinedInConstructor() = bernoulliDistribution(0.5)

    fun hasDefaultValue() = false

    fun isVar() = bernoulliDistribution(0.5)

    private fun useCustomClass() = bernoulliDistribution(0.5)

    private fun useBasicType() = bernoulliDistribution(0.3)

    /**
     * Whether to to use `bar` in a `foo` function call in the following situation:
     *
     * ```
     * fun foo(bar: T = baz)
     * ```
     */
    fun provideArgumentWithDefaultValue() = bernoulliDistribution(0.5)

    // tmp
    private fun inheritedClassCount() = 0

    // tmp
    private fun inheritClass() = bernoulliDistribution(0.5)

    // tables

    enum class ClassKind {
        DATA, INTERFACE, ENUM, REGULAR
    }

    val classKindTable = ProbabilityTable(ClassKind.values())

    enum class Visibility {
        PUBLIC, PROTECTED, PRIVATE;

        override fun toString() = this.name.lowercase()
    }

    val propertyVisibilityTable = ProbabilityTable(Visibility.values())

    val nonPrivatePropertyVisibilityTable = ProbabilityTable(listOf(Visibility.PUBLIC, Visibility.PROTECTED))

    val varianceTable = ProbabilityTable(Variance.values())

    // functions with complex logic

    fun chooseType(context: Context, typeParameterList: List<KtTypeParameter>, depth: Int = 0): ClassOrBasicType {
        val canUseTypeParameter = typeParameterList.any { it.variance != Variance.IN_VARIANCE }
        val canUseCustomClass = context.customClasses.any { !it.isAbstract() }
        return when {
            (!canUseCustomClass && !canUseTypeParameter) || useBasicType() -> ClassOrBasicType(BasicTypeGenerator().generate())
            canUseCustomClass && (!canUseTypeParameter || useCustomClass()) -> {
                val cls = context.customClasses.filter { !it.isAbstract() }.random()
                return ClassOrBasicType(cls.getFullyQualifiedName(context, emptyList(), false, depth + 1).first, cls)
            }
            else -> {
                ClassOrBasicType(typeParameterList.filter { it.variance != Variance.IN_VARIANCE }.random().name!!)
            }
        }
    }

    fun resolveTypeParameters(
        cls: KtClass,
        context: Context,
        typeParameterList: List<KtTypeParameter>,
        depth: Int = 0
    ): Pair<ClassOrBasicType, List<ClassOrBasicType>> {
        val typeParameters =
            cls.typeParameterList?.parameters?.map { randomTypeParameterValue(it, context, typeParameterList, depth) }
        val tmp = typeParameters?.joinToString(", ", "<", ">") { it.name } ?: ""
        return Pair(
            ClassOrBasicType(cls.name!! + tmp, cls),
            typeParameters ?: emptyList()
        )
    }

    fun randomConst(type: ClassOrBasicType, context: Context): String {
        TODO("Will use other generator")
    }

    private fun randomTypeParameterValue(
        typeParameter: KtTypeParameter,
        context: Context,
        typeParameterList: List<KtTypeParameter>,
        depth: Int
    ): ClassOrBasicType {
        return when {
            typeParameter.extendsBound != null -> {
                // temporary until proper inheritance is implemented
                ClassOrBasicType(typeParameter.extendsBound!!.name!!)
            }
//            cls.parameterValues.containsKey(typeParameter) -> {
//                ClassWithTypeParameters(cls.parameterValues[typeParameter]!!)
//            }
            depth >= maxTypeParameterDepth -> {
                ClassOrBasicType(BasicTypeGenerator().generate())
            }
            else -> {
                chooseType(context, typeParameterList, depth + 1)
            }
        }
    }

    // TODO: inheritance conflicts?
    // TODO: O(context.customClasses.size), could be O(inheritedClassCount)
    fun inheritedClasses(context: Context): List<KtClass> {
        val inheritedClassCount = inheritedClassCount()
        if (inheritedClassCount == 0) {
            return emptyList()
        }
        val result = mutableListOf<KtClass>()
        if (inheritClass() && context.customClasses.any { it.isInheritableClass() }) {
            result.add(context.customClasses.filter { it.isInheritableClass() }.random())
        }
        result.addAll(context.customClasses.filter { it.isInterface() }
            .shuffled()
            .let {
                it.subList(0, min(inheritedClassCount - 1, it.size))
            })
        return result
    }
}