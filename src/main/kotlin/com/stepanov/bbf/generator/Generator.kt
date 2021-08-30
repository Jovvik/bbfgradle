package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.tce.StdLibraryGenerator
import com.stepanov.bbf.bugfinder.util.addImport
import org.jetbrains.kotlin.psi.KtFile

class Generator {
    fun generate(): Pair<Project, Boolean> {
        val context = Context()
        val project = Project.createFromCode("")
        val file = project.files.first().psiFile as KtFile
        ClassGenerator(context, file).generate()
        val imports = StdLibraryGenerator.calcImports(file)
        imports.forEach {
            file.addImport(it, false)
        }
        return project to imports.any { it.startsWith("java") }
    }
}