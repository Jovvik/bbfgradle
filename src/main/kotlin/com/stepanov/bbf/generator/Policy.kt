package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.KotlinType
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

    // soft limits

    fun classLimit() = 15

    fun enumValueLimit() = uniformDistribution(1, 10)

    fun freeFunctionLimit() = uniformDistribution(1, 10)

    fun functionParameterLimit() = uniformDistribution(0, 3)

    fun functionLimit() = uniformDistribution(0, 3)

    fun nestedClassLimit() = uniformDistribution(0, 3)

    fun propertyLimit() = uniformDistribution(1, 4)

    fun typeParameterLimit() = uniformDistribution(0, 3)

    // stuff

    fun isAbstractClass() = bernoulliDistribution(0.4)

    // tmp until instance generator
    fun isAbstractProperty() = bernoulliDistribution(1.0)

    fun isAbstractFunction() = bernoulliDistribution(0.5)

    fun isDefinedInConstructor() = bernoulliDistribution(0.5)

    fun isInfixFunction() = bernoulliDistribution(0.5)

    fun isInlineFunction() = bernoulliDistribution(0.2)

    fun isInner() = bernoulliDistribution(0.3)

    fun isOpen() = bernoulliDistribution(0.1)

    fun isSealed() = bernoulliDistribution(0.1)

    fun isVar() = bernoulliDistribution(0.5)

    fun hasDefaultValue() = false

    /**
     * Whether to use `bar` in a `foo` function call in the following situation:
     *
     * ```
     * fun foo(bar: T = baz)
     * ```
     */
    // TODO: how come there's no usage
    fun provideArgumentWithDefaultValue() = bernoulliDistribution(0.5)

    private fun inheritedClassCount() = uniformDistribution(0, 3)

    private fun inheritClass() = bernoulliDistribution(0.5)

    private fun useTypeParameter() = bernoulliDistribution(0.3)

    fun useBound() = bernoulliDistribution(0.3)

    // tables

    enum class ClassKind {
        DATA, INTERFACE, ENUM, REGULAR
    }

    val classKind = ProbabilityTable(ClassKind.values())

    enum class Visibility {
        PUBLIC, PROTECTED, PRIVATE;

        override fun toString() = this.name.lowercase()
    }

    val propertyVisibility = ProbabilityTable(Visibility.values())

    val variance = ProbabilityTable(Variance.values())

    // functions with complex logic

    fun chooseType(typeParameterList: List<KtTypeParameter>, vararg allowedVariance: Variance): KtTypeOrTypeParam {
        val typeParameter = typeParameterList.filter { it.variance in allowedVariance }.randomOrNull()
        return if (typeParameter != null && useTypeParameter()) {
            KtTypeOrTypeParam.Parameter(typeParameter)
        } else {
            KtTypeOrTypeParam.Type(RandomTypeGenerator.generateRandomTypeWithCtx()!!)
        }
    }


    fun resolveTypeParameters(cls: KtClass): Pair<String, List<KotlinType>> {
        val typeParameters = cls.typeParameterList?.parameters?.mapNotNull { randomTypeParameterValue(it) }
        return Pair(
            cls.name!! + typeParameters?.joinToString(", ", "<", ">").orEmpty(),
            typeParameters.orEmpty()
        )
    }

    fun randomConst(type: KotlinType, context: Context): String {
        TODO("Will use other generator")
    }

    private fun randomTypeParameterValue(typeParameter: KtTypeParameter): KotlinType? {
        return RandomTypeGenerator.generateRandomTypeWithCtx(typeParameter.extendsBound?.text?.let {
            RandomTypeGenerator.generateType(it)
        })
    }

    // TODO: inheritance conflicts?
    // TODO: O(context.customClasses.size), could be O(inheritedClassCount)
    fun inheritedClasses(context: Context): List<KtClass> {
        val inheritedClassCount = inheritedClassCount()
        if (inheritedClassCount == 0) {
            return emptyList()
        }
        val result = mutableListOf<KtClass>()
        val inheritedClass = context.customClasses.filter { it.isInheritableClass() }.randomOrNull()
        if (inheritClass() && inheritedClass != null) {
            result.add(inheritedClass)
        }
        result.addAll(context.customClasses.filter { it.isInterface() }
                .shuffled()
                .let {
                    it.subList(0, min(inheritedClassCount - 1, it.size))
                })
        return result
    }
}