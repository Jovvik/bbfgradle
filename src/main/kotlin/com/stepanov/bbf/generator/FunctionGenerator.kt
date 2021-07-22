package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import com.stepanov.bbf.generator.Policy.chooseType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

class FunctionGenerator(val context: Context, val file: KtFile, val containingClass: KtClass? = null) {
    fun generate(index: Int) {
        val returnType = chooseType()
        // pasted
        val typeParameters = (0 until Policy.typeParameterLimit()).map {
            Factory.psiFactory.createTypeParameter("T_$it")
        }
        val fn =
            Factory.psiFactory.createFunction("fun ${if (typeParameters.isEmpty()) "" else "<> "}${getName(index)}(): ${returnType.name} = TODO()")
        typeParameters.forEach { fn.typeParameters.add(it) }
        repeat(Policy.functionParameterLimit()) {
            // doesn't do what's expected
            fn.valueParameterList!!.add(Factory.psiFactory.createParameter("param_$it : ${chooseType()}"))
        }
        if (containingClass != null) {
            containingClass.addPsiToBody(fn)
        } else {
            file.addAtTheEnd(fn)
        }
    }

    private fun chooseType() = chooseType(context, containingClass?.typeParameters ?: emptyList())

    private fun getName(index: Int): String {
        return if (containingClass == null) {
            "f_${context.customFunctions.size}"
        } else {
            "f_${context.customClasses.size}_$index"
        }
    }
}