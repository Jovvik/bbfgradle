package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.executor.project.Project
import org.jetbrains.kotlin.psi.KtFile

class Generator {
    lateinit var context: Context
    lateinit var file: KtFile

    fun generate(): Project {
        context = Context()
        val project = Project.createFromCode("")
        file = project.files.first().psiFile as KtFile
        ClassGenerator(context, file).generate()
        return project
    }
}