package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.Variance
import java.lang.Integer.min
import kotlin.random.Random

object Policy {
    fun classLimit() = 30

    fun isAbstract() = bernoulliDistribution(0.5)

    fun isDataclass() = bernoulliDistribution(0.5)

    fun isEnum() = bernoulliDistribution(0.3)

    fun enumValueLimit() = uniformDistribution(1, 10)

    fun isInner() = bernoulliDistribution(0.3)

    // tmp
    fun isOpen() = bernoulliDistribution(0.0)

    fun propertyLimit() = uniformDistribution(5, 10)

    fun nestedClassLimit() = uniformDistribution(1, 3)

    // TODO
    fun isSealed() = false

    const val maxNestedClassDepth = 3

    const val maxTypeParameterDepth = 2

    // tmp until instance generator
    fun isAbstractProperty() = bernoulliDistribution(1.0)

    fun isDefinedInConstructor() = bernoulliDistribution(0.5)

    fun hasDefaultValue() = false

    fun isVar() = bernoulliDistribution(0.5)

    private fun useCustomClass() = bernoulliDistribution(0.5)

    private fun useBasicType() = bernoulliDistribution(0.3)

    // tmp
    fun typeParameterLimit() = 0

    /**
     * Whether to to use `bar` in a `foo` function call in the following situation:
     *
     * ```
     * fun foo(bar: T = baz)
     * ```
     */
    fun provideArgumentWithDefaultValue() = bernoulliDistribution(0.5)

    // tmp
    private fun inheritedClassCount() = uniformDistribution(1, 3)

    // tmp
    private fun inheritClass() = bernoulliDistribution(0.5)

    private fun uniformDistribution(min: Int, max: Int): Int {
        return Random.nextInt(min, max)
    }

    private fun bernoulliDistribution(p: Double): Boolean {
        return Random.nextDouble() < p
    }

    // TODO: add probability tweaking
    // TODO: proper typing
    fun propertyVisibility(): String {
        return listOf("public", "protected", "private").random()
    }

    fun variance(): Variance {
        return listOf(Variance.INVARIANT, Variance.IN_VARIANCE, Variance.OUT_VARIANCE).random()
    }

    // TODO: nullable versions
    fun chooseType(context: Context, typeParameterList: List<KtTypeParameter>, depth: Int = 0): ClassOrBasicType {
        val canUseTypeParameter = typeParameterList.any { it.variance != Variance.IN_VARIANCE }
        val canUseCustomClass = context.customClasses.any { !it.isAbstract() }
        return when {
            (!canUseCustomClass && !canUseTypeParameter) || useBasicType() -> ClassOrBasicType(BasicTypeGenerator().generate())
            canUseCustomClass && (!canUseTypeParameter || useCustomClass()) -> {
                val cls = context.customClasses.filter { !it.isAbstract() }.random()
                return ClassOrBasicType(cls.getFullyQualifiedName(context, emptyList(), false, depth + 1), cls)
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
    ): ClassOrBasicType {
        val typeParameters = cls.typeParameterList?.parameters?.joinToString(", ", "<", ">") {
            randomTypeParameterValue(it, context, typeParameterList, depth).name
        } ?: ""
        return ClassOrBasicType(cls.name!! + typeParameters, cls)
    }

    fun randomConst(type: ClassOrBasicType, context: Context): String {
        TODO("Will use other generator")
        /*if (type.hasTypeParameters) {
            throw IllegalArgumentException("Cannot generate constants of generic types")
        }
        val typeName = type.name.substringAfterLast(".")
        return when (typeName) {
            // TODO: generate constants either with small magnitude or around limits
            "Int" -> Random.nextInt()
            "Long" -> Random.nextLong()
            "Boolean" -> Random.nextBoolean()
            // TODO: more interesting distribution
            "Float" -> Random.nextFloat().toString() + "f"
            "Double" -> Random.nextDouble()
            else -> {
                println(type.name)
                val cls = type.cls ?: throw IllegalArgumentException("Type is not basic but has no associated KtClass")
                return if (cls.isEnum()) {
                    cls.getFullyQualifiedName(context, false) + "." +
                            cls.declarations.filterIsInstance<KtEnumEntry>().random().name
                } else {
                    val parameters = cls.getPrimaryConstructorParameterList()!!.parameters
                    parameters.joinToString(
                        ", ",
                        "${cls.getFullyQualifiedName(context, false)}(",
                        ")"
                    ) { randomConst(ClassOrBasicType(it.typeReference!!.text), context) }
                }
            }
        }.toString()*/
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

    // TODO: resolve inheritance conflicts?
    // TODO: O(context.customClasses.size), could be O(inheritedClassCount)
    fun inheritedClasses(context: Context): List<KtClass> {
        val inheritedClassCount = inheritedClassCount()
        if (inheritedClassCount == 0) {
            return emptyList()
        }
        val result = mutableListOf<KtClass>()
        if (useCustomClass() && context.customClasses.any { it.isInheritableClass() }) {
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