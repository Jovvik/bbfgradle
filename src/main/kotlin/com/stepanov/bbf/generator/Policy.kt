package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.generator.Policy.Arithmetic.ConstKind.*
import com.stepanov.bbf.generator.Policy.Arithmetic.ConstType.*
import com.stepanov.bbf.generator.arithmetic.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import java.lang.Integer.min
import kotlin.random.Random
import kotlin.reflect.full.primaryConstructor

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

    fun arithmeticExpressionLimit() = uniformDistribution(1, 4)

    fun classLimit() = 10

    fun enumValueLimit() = uniformDistribution(1, 10)

    // tmp
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

    object Arithmetic {
        private enum class ConstType {
            INT, LONG, FLOAT, DOUBLE
        }

        private val constType = ProbabilityTable(ConstType.values())

        private enum class ConstKind {
            SMALL, LARGE_POSITIVE, LARGE_NEGATIVE
        }

        private val constKind = ProbabilityTable(ConstKind.values())

        fun const(): String {
            fun maxValue(type: ConstType): Long = when (type) {
                INT -> Int.MAX_VALUE.toLong()
                LONG -> Long.MAX_VALUE
                else -> throw IllegalArgumentException()
            }

            fun minValue(type: ConstType): Long = when (type) {
                INT -> Int.MIN_VALUE.toLong()
                LONG -> Long.MIN_VALUE
                else -> throw IllegalArgumentException()
            }

            val kind = constKind()
            val type = constType()
            return when (kind) {
                SMALL -> when (type) {
                    INT, LONG -> Random.nextLong(-5, 6)
                    FLOAT, DOUBLE -> Random.nextDouble() - 0.5
                }
                LARGE_POSITIVE -> when (type) {
                    INT, LONG -> Random.nextLong(maxValue(type) - 10, maxValue(type)) + 1
                    FLOAT, DOUBLE -> (Random.nextDouble() + 1) * maxValue(type) * 0.5
                }
                LARGE_NEGATIVE -> when (type) {
                    INT, LONG -> Random.nextLong(minValue(type), minValue(type) + 10)
                    FLOAT, DOUBLE -> (Random.nextDouble() + 1) * minValue(type) * 0.5
                }
            }.toString() + when (type) {
                LONG -> "L"
                FLOAT -> "f"
                else -> ""
            }
        }

        private val nodeTable =
            ProbabilityTable(BinaryOperator::class.sealedSubclasses + UnaryOperator::class.sealedSubclasses)

        fun node(context: Context, depth: Int = 0): Node {
            return if (Random.nextDouble() < 1 / (depth.toDouble() + 2) || depth >= 5) {
                if (Random.nextDouble() < 0.5 && context.visibleNumericVariables.isNotEmpty()) {
                    Variable(context, depth)
                } else {
                    Const(context, depth)
                }
            } else {
                nodeTable().primaryConstructor!!.call(context, depth)
            }
        }
    }

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