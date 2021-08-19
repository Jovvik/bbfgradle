package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.generator.targetsgenerators.typeGenerators.RandomTypeGenerator
import com.stepanov.bbf.generator.Policy.Arithmetic.ConstKind.LARGE_POSITIVE
import com.stepanov.bbf.generator.Policy.Arithmetic.ConstKind.SMALL
import com.stepanov.bbf.generator.arithmetic.*
import com.stepanov.bbf.generator.arithmetic.Type.*
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
        val type = ProbabilityTable(Type.values())
        val signedType = ProbabilityTable(Type.values().filter { !it.isUnsigned })
        val unsignedType = ProbabilityTable(Type.values().filter { it.isUnsigned })

        private enum class ConstKind {
            SMALL, LARGE_POSITIVE, /*LARGE_NEGATIVE*/
        }

        private val constKind = ProbabilityTable(ConstKind.values())
        private val nonNegativeConstKind = ProbabilityTable(listOf(SMALL, LARGE_POSITIVE))

        private const val largeConstantSpread = 10
        private const val smallConstantSpread = 5L

        /**
         * Always positive since negative literals don't exist.
         */
        fun const(type: Type): String {
            val kind = if (type.isUnsigned) nonNegativeConstKind() else constKind()
            return when (kind) {
                SMALL -> when {
                    type.isFloatingPoint -> Random.nextDouble()
                    else -> Random.nextLong(smallConstantSpread * 2 + 1)
                }
                LARGE_POSITIVE -> when {
                    type.isFloatingPoint -> (Random.nextDouble() + 1) * 0.5 * type.maxValue.toDouble()
                    type == ULONG -> (Random.nextLong(
                        Long.MAX_VALUE - largeConstantSpread / 2,
                        Long.MAX_VALUE
                    ) + 1).toULong() * 2u
                    else -> Random.nextLong(type.maxValue.toLong() - largeConstantSpread, type.maxValue.toLong()) + 1
                }
                /*LARGE_NEGATIVE -> when {
                    type.isFloatingPoint -> (Random.nextDouble() + 1) * 0.5 * type.minValue.toDouble()
                    type == LONG -> Random.nextLong(
                        type.minValue.toLong(),
                        type.minValue.toLong() + largeConstantSpread
                    ) + 1
                    else -> Random.nextLong(type.minValue.toLong(), type.minValue.toLong() + largeConstantSpread)
                }*/
            }.toString() + if (type.isUnsigned) "u" else "" + when (type) {
                LONG -> "L"
                FLOAT -> "f"
                else -> ""
            }
        }

        private const val depthLimit = 5

        private fun isVariable() = bernoulliDistribution(0.5)

        private val nodeTable =
            ProbabilityTable(BinaryOperator::class.sealedSubclasses + UnaryOperator::class.sealedSubclasses)

        fun node(context: Context, depth: Int = 0): Node {
            return if (Random.nextDouble() < 1 / (depth.toDouble() + 2) || depth >= depthLimit) {
                if (isVariable() && context.visibleNumericVariables.isNotEmpty()) {
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
            val generatedType = RandomTypeGenerator.generateRandomStandardTypeWithCtx()
            if (generatedType == null) {
                chooseType(typeParameterList, *allowedVariance)
            } else {
                KtTypeOrTypeParam.Type(generatedType)
            }
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
        val bound = typeParameter.extendsBound?.text?.let {
            RandomTypeGenerator.generateType(it)
        }
        return RandomTypeGenerator.generateRandomStandardTypeWithCtx(bound)
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