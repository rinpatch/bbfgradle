package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.reduktor.parser.PSICreator
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.resolve.BindingContext

abstract class Transformation {
    abstract fun transform()

    companion object {
        lateinit var checker: MutationChecker
        val file: PsiFile
            get() = checker.curFile.psiFile
        val project: Project
            get() = checker.project
        var ctx: BindingContext? = null
        internal val log = LogManager.getLogger("mutatorLogger")

        fun updateCtx() {
            ctx = PSICreator.analyze(file, project)
        }
    }

}