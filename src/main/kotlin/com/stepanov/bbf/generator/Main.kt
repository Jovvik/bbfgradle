package com.stepanov.bbf.generator

import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator
import org.jetbrains.kotlin.psi.KtFile
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    //Init log4j
    PropertyConfigurator.configure("src/main/resources/bbfLog4j.properties")
    if (!CompilerArgs.getPropAsBoolean("LOG")) {
        Logger.getRootLogger().level = Level.OFF
        Logger.getLogger("bugFinderLogger").level = Level.OFF
        Logger.getLogger("mutatorLogger").level = Level.OFF
        Logger.getLogger("reducerLogger").level = Level.OFF
        Logger.getLogger("transformationManagerLog").level = Level.OFF
    }

//    val p_ = Project.createFromCode(
//        """
//        abstract class A() {
//            abstract val a: Int
//        }
//
//        class B() : A() {
//            override val a: Int = 5
//        }
//    """.trimIndent()
//    )
//    val file_ = p_.files.first().psiFile as KtFile
//    val bindingContext = PSICreator.analyze(file_)!!
//    for (cls in file_.getAllPSIChildrenOfType<KtClass>()) {
//        val classDescriptor = cls.getDeclarationDescriptorIncludingConstructors(bindingContext)!! as ClassDescriptor
//        val descrs = classDescriptor.unsubstitutedMemberScope.getDescriptorsFiltered { true }
//        println(descrs.filterIsInstance<PropertyDescriptor>())
//    }
//
//    exitProcess(0)

    val p = Generator().generate()
    val file = p.files.first().psiFile as KtFile
    println(file.text)
    val compiler = JVMCompiler("")
    val compiled = compiler.tryToCompile(p)
    if (!compiled.isCompileSuccess) {
        println(compiled.combinedOutput)
    }
    exitProcess(0)
}