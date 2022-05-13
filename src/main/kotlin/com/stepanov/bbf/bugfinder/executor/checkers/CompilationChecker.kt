package com.stepanov.bbf.bugfinder.executor.checkers

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.executor.COMPILE_STATUS
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.manager.Bug
import com.stepanov.bbf.bugfinder.manager.BugManager
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.tracer.Tracer
import com.stepanov.bbf.bugfinder.util.StatisticCollector
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import coverage.MyMethodBasedCoverage
import org.apache.log4j.Logger
import kotlin.math.absoluteValue

//Project adaptation
open class CompilationChecker(private val compilers: List<CommonCompiler>) {

    constructor(compiler: CommonCompiler) : this(listOf(compiler))

    //Back compatibility
    fun checkTextCompiling(text: String): Boolean = checkCompilingWithBugSaving(Project.createFromCode(text), null)
    fun checkCompilingWithBugSaving(file: PsiFile): Boolean = checkTextCompiling(file.text)


    private fun createPsiAndCheckOnErrors(text: String, language: LANGUAGE): Boolean =
        when (language) {
            LANGUAGE.JAVA -> PSICreator.getPsiForJava(text, Factory.file.project)
            else -> Factory.psiFactory.createFile(text)
        }.let { tree ->
            tree.getAllPSIChildrenOfType<PsiErrorElement>().isEmpty() && additionalConditions.all { it.invoke(tree) }
        }

    //FALSE IF ERROR
    private fun checkSyntaxCorrectnessAndAddCond(project: Project, curFile: BBFFile?) =
        curFile?.let {
            createPsiAndCheckOnErrors(curFile.text, curFile.getLanguage())
        } ?: project.files.any { createPsiAndCheckOnErrors(it.text, it.getLanguage()) }


    fun checkCompiling(project: Project): Boolean {
        val allTexts = project.files.map { it.psiFile.text }.joinToString()
        checkedConfigurations[allTexts]?.let { log.debug("Already checked"); return it }
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, null)) {
            log.debug("Wrong syntax or breaks conditions")
            checkedConfigurations[allTexts] = false
            return false
        }
        val statuses = compileAndGetStatuses(project)
        return if (statuses.all { it == COMPILE_STATUS.OK }) {
            checkedConfigurations[allTexts] = true
            true
        } else {
            checkedConfigurations[allTexts] = false
            false
        }
    }

//    private fun compareCompilerWorkingTimes(compilerWorkingTimes: List<Long>, isExecution: Boolean): Boolean {
//        if (isExecution) println("EXECUTION!!")
//        for (i in compilers.indices) {
//            for (j in i + 1 until compilers.size) {
//                val fExTime = compilerWorkingTimes[i]
//                val sExTime = compilerWorkingTimes[j]
//                val slowerBackend = if (fExTime > sExTime) i else j
//                val fasterBackend = i + j - slowerBackend
//                val compilationTimeDifference = abs(fExTime - sExTime)
//                val compilationTimeDifferenceInPercents =
//                    if (fExTime == sExTime || fExTime == 0L || sExTime == 0L) 0.0
//                    else maxOf(fExTime, sExTime).toDouble() / minOf(fExTime, sExTime) * 100.0
//                println("BACKEND ${compilers[slowerBackend]} slower then ${compilers[fasterBackend]} on $compilationTimeDifference ms in percents $compilationTimeDifferenceInPercents")
//                return true // TODO!!
//            }
//        }
//        return true
//    }

    fun checkCompilingWithBugSaving(project: Project, curFile: BBFFile? = null): Boolean {
        log.debug("Compilation checking started")
        val allTexts = project.files.map { it.psiFile.text }.joinToString()
        checkedConfigurations[allTexts]?.let { log.debug("Already checked"); return it }
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, curFile)) {
            log.debug("Wrong syntax or breaks conditions")
            StatisticCollector.incField("Incorrect programs")
            checkedConfigurations[allTexts] = false
            return false
        }
        val (statuses, compilationTimes) =
            compileAndGetStatusesWithExecutionTime(project).let { it.map { it.first } to it.map { it.second } }
        when {
            statuses.all { it == COMPILE_STATUS.OK } -> {
                if (CompilerArgs.isPerformanceMode) {
                    val jvmCompilers = compilers.map {it as JVMCompiler}
                    val results = PerformanceOracle.profileProject(project, jvmCompilers)
                    if (results != null) {
                       BugManager.saveBug(Bug(compilers, "", project, BugType.PERFORMANCE))
                    }
                }
                if (CompilerArgs.isGuidedByCoverage) {
                    if (isCoverageDecreases(project)) {
                        checkedConfigurations[allTexts] = false
                        return false
                    }
                }
                if (CompilerArgs.isMiscompilationMode) {
                    val checkRes = checkTraces(project)
                    checkedConfigurations[allTexts] = checkRes
                    return checkRes
                }
                StatisticCollector.incField("Correct programs")
                checkedConfigurations[allTexts] = true
                return true
            }
            statuses.all { it == COMPILE_STATUS.ERROR } -> {
                StatisticCollector.incField("Incorrect programs")
                checkedConfigurations[allTexts] = false
                return false
            }
        }
        //TODO!!
        checkAndGetCompilerBugs(project).forEach { BugManager.saveBug(it) }
        checkedConfigurations[allTexts] = false
        StatisticCollector.incField("Correct programs")
        return false
    }

    fun isCompilationSuccessful(project: Project): Boolean = compilers.all { it.checkCompiling(project) }

    fun compileAndGetMessage(project: Project): String = compilers.first().getErrorMessage(project)

    private fun compileAndGetStatuses(project: Project): List<COMPILE_STATUS> =
        compilers.map { it.tryToCompileWithStatus(project) }

    private fun compileAndGetStatusesWithExecutionTime(project: Project): List<Pair<COMPILE_STATUS, Long>> =
        compilers.map { it.tryToCompileWithStatusAndExecutionTime(project) }


    fun checkTraces(project: Project): Boolean {
        val copyOfProject = project.copy()
        Tracer(compilers.first(), copyOfProject).trace()
        return TracesChecker(compilers).checkBehavior(copyOfProject)
    }


    fun isCoverageDecreases(project: Project): Boolean {
        val sumCoverage = CoverageGuider.getCoverage(project, compilers)
        MyMethodBasedCoverage.methodProbes.clear()
        val k = CoverageGuider.calcKoefOfCoverageUsage(sumCoverage)
        println("k = $k")
        if (k > CoverageGuider.initCoef) CoverageGuider.initCoef = k
        return k < CoverageGuider.initCoef
    }

    private fun checkAndGetCompilerBugs(project: Project): List<Bug> {
        val res = mutableListOf<Bug>()
        compilers.forEach { compiler ->
            if (compiler.isCompilerBug(project)) {
                val msg = compiler.getErrorMessage(project)
                val type =
                    if (msg.contains("Exception while analyzing expression")) BugType.FRONTEND else BugType.BACKEND
                res.add(Bug(compiler, msg, project.copy(), type))
            }
        }
        if (res.size != 0) return res
        val compilersToStatus = compilers.map { it to it.checkCompiling(project) }
        val grouped = compilersToStatus.groupBy { it.first.compilerInfo.split(" ").first() }
        for (g in grouped) {
            if (g.value.map { it.second }.toSet().size != 1) {
                val diffCompilers =
                    g.value.groupBy { it.second }.mapValues { it.value.first().first }.values.toList()
                res.add(
                    Bug(diffCompilers, "", project.copy(), BugType.DIFFCOMPILE)
                )
            }
        }
        return res
    }

//        fun checkABI(project: Project): Pair<Int, File>? {
//            val compilers = CompilerArgs.getCompilersList()
//            if (compilers.size != 2) return null
//            val compiled = compilers.mapIndexed { index, comp ->
//                comp.pathToCompiled =
//                    comp.pathToCompiled.replace(".jar", "$index.jar")
//                comp.compile(project).pathToCompiled
//            }
//            if (compiled.any { it == "" }) return null
//            val jars = compiled.map { JarFile(it) }
//            val task =
//                JarTask(
//                    "",
//                    jars.first(),
//                    jars.last(),
//                    compilers.first().compilerInfo,
//                    compilers.last().compilerInfo,
//                    File("tmp/report.html"),
//                    checkerConfiguration {}
//                )
//            val execRes = task.execute()
//            MySummaryReport.summary.add(task.defectReport)
//            return execRes
//        }

    val additionalConditions: MutableList<(PsiFile) -> Boolean> = mutableListOf()

    private val checkedConfigurations = hashMapOf<String, Boolean>()
    private val log = Logger.getLogger("mutatorLogger")
}