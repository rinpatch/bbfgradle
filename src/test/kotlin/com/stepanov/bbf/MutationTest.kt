package com.stepanov.bbf

import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.Mutator
import com.stepanov.bbf.bugfinder.mutator.transformations.MutationExample
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import org.junit.Test
import java.io.File
import kotlin.system.exitProcess
import kotlin.test.assertEquals

class MutationTest {

    val pathToTestFile = "src/test/testData/myTest.kt"

    @Test
    fun checkMutation() {
        val project = Project.createFromCode(File(pathToTestFile).readText())
        val checker = MutationChecker(JVMCompiler(), project)
        Transformation.checker = checker
        println("Before = $project")
        // Commented out because executeMutation is private => Build fails with it
        //  Mutator(project).executeMutation(MutationExample(), 100)
        println("After = $project")
    }
}