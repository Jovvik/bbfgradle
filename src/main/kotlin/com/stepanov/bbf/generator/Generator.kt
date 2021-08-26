package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.addImport
import org.jetbrains.kotlin.psi.KtFile

class Generator {
    fun generate(): Pair<Project, Boolean> {
        val context = Context()
        val project = Project.createFromCode("")
        val file = project.files.first().psiFile as KtFile
        ClassGenerator(context, file).generate()
        val packages = Policy.importPackages.mapNotNull { (cls, packageName) ->
            if (file.text.contains(cls)) {
                packageName
            } else null
        }.distinct()
        packages.forEach {
            file.addImport(it, true)
        }
        return project to packages.any { it.contains("java") }
    }
}