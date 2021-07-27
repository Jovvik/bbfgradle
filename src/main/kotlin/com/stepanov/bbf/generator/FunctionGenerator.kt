package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addPsiToBody
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.Variance

class FunctionGenerator(val context: Context, val file: KtFile, val containingClass: KtClass? = null) {
    fun generate(index: Int) {
        val typeParameters = (0 until Policy.typeParameterLimit()).map {
            Factory.psiFactory.createTypeParameter("T_$it")
        }
        val returnType = chooseType(typeParameters, false)
        val fn =
            Factory.psiFactory.createFunction("fun ${if (typeParameters.isEmpty()) "" else "<> "}${getName(index)}(): ${returnType.name} = TODO()")
        typeParameters.forEach { fn.typeParameterList!!.addParameter(it) }
        repeat(Policy.functionParameterLimit()) {
            val chosenType = chooseType(typeParameters, true)
            fn.valueParameterList!!.addParameter(Factory.psiFactory.createParameter("param_$it : ${chosenType.name}"))
        }
        if (containingClass != null) {
            containingClass.addPsiToBody(fn)
        } else {
            file.addAtTheEnd(fn)
        }
    }

    private fun chooseType(typeParameters: List<KtTypeParameter>, isValueArgument: Boolean): KtTypeOrTypeParam {
        return Policy.chooseType(
            typeParameters + containingClass?.typeParameters.orEmpty(),
            Variance.INVARIANT,
            if (isValueArgument) Variance.IN_VARIANCE else Variance.OUT_VARIANCE
        )
    }

    private fun getName(index: Int): String {
        return if (containingClass == null) {
            "f_${context.customFunctions.size}"
        } else {
            "f_${context.customClasses.size}_$index"
        }
    }
}