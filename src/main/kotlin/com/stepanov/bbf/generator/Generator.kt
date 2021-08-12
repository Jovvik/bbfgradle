package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.executor.project.Project
import org.jetbrains.kotlin.psi.KtFile

class Generator {
    fun generate(): Project {
        val context = Context()
//        val project = Project.createFromCode("import kotlin.reflect.*") // for `KMutablePropertyX`
        val project = Project.createFromCode("")
        val file = project.files.first().psiFile as KtFile
        ClassGenerator(context, file).generate()
        return project
    }
}