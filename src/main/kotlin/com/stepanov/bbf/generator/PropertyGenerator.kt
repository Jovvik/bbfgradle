package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass

class PropertyGenerator(val context: Context, val cls: KtClass, val parentGenerator: ClassGenerator) {
    fun generate(propertyIndex: Int) {
        val modifiers = mutableListOf(Policy.propertyVisibility())
        val type = Policy.chooseType(context, cls.typeParameters)
        val name = indexString("property", propertyIndex, context)
        val typeParameter = cls.typeParameters.firstOrNull { it.name == type.name }
        if (typeParameter != null || type.hasTypeParameters || Policy.isDefinedInConstructor()) {
            parentGenerator.addConstructorArgument(name, type, cls, typeParameter)
        } else {
            cls.addPsiToBody(
                Factory.psiFactory.createProperty(
                    modifiers.joinToString(" "),
                    name,
                    type.name,
                    Policy.isVar(),
                    Policy.randomConst(type, context)
                )
            )
        }
    }
}