package com.stepanov.bbf.generator

import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.types.Variance
import kotlin.random.Random

object Policy {
    fun classLimit() = 50

    fun isAbstract() = bernoulliDistribution(0.2)

    fun isDataclass() = bernoulliDistribution(0.5)

    fun isEnum() = bernoulliDistribution(0.3)

    fun enumValueLimit() = uniformDistribution(1, 10)

    fun isInner() = bernoulliDistribution(0.3)

    fun isOpen() = bernoulliDistribution(0.5)

    fun propertyLimit() = uniformDistribution(5, 10)

    fun nestedClassLimit() = uniformDistribution(1, 3)

    // TODO
    fun isSealed() = false

    const val maxNestedClassDepth = 3

    private fun isAbstractProperty() = bernoulliDistribution(0.2)

    // temporary until object generator is merged
    fun isDefinedInConstructor() = true

    fun hasDefaultValue() = false

    fun isVar() = bernoulliDistribution(0.5)

    private fun useCustomClass() = bernoulliDistribution(0.5)

    private fun useBasicType() = bernoulliDistribution(0.3)

    fun typeParameterLimit() = uniformDistribution(0, 2)

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
    fun chooseType(context: Context, typeParameterList: List<KtTypeParameter>): ClassOrBasicType {
        val canUseTypeParameter = typeParameterList.any { it.variance != Variance.IN_VARIANCE }
        val canUseCustomClass = context.customClasses.any { !it.isAbstract() }
        return when {
            (!canUseCustomClass && !canUseTypeParameter) || useBasicType() -> ClassOrBasicType(basicTypes.random())
            canUseCustomClass && (!canUseTypeParameter || useCustomClass()) -> {
                val cls = context.customClasses.random()
                val typeParameters = cls.typeParameterList?.parameters?.joinToString(", ", "<", ">") {
                    randomTypeParameterValue(it, context, cls.typeParameters).name
                } ?: ""
                return ClassOrBasicType(cls.getFullyQualifiedName(context, false) + typeParameters, cls)
            }
            else -> {
                ClassOrBasicType(typeParameterList.filter { it.variance != Variance.IN_VARIANCE }.random().name!!)
            }
        }
    }

    // dead code
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
        typeParameterList: List<KtTypeParameter>
    ): ClassOrBasicType {
        return when {
            typeParameter.extendsBound != null -> {
                // temporary until proper inheritance is implemented
                ClassOrBasicType(typeParameter.extendsBound!!.name!!)
            }
//            cls.parameterValues.containsKey(typeParameter) -> {
//                ClassWithTypeParameters(cls.parameterValues[typeParameter]!!)
//            }
            else -> {
                chooseType(context, typeParameterList)
            }
        }
    }

    // floats might be too complicated, YARPGEN doesn't use them
    // can't generate random valid chars easily
    private val basicTypes = listOf("Int", "Long", "Float", "Double", "Boolean")
}