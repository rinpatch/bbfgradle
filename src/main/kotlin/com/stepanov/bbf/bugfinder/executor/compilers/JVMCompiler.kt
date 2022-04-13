package com.stepanov.bbf.bugfinder.executor.compilers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.CompilationResult
import com.stepanov.bbf.bugfinder.executor.project.Directives
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.Stream
import com.stepanov.bbf.bugfinder.util.copyFullJarImpl
import com.stepanov.bbf.bugfinder.util.writeRuntimeToJar
import com.stepanov.bbf.coverage.CompilerInstrumentation
import com.stepanov.bbf.reduktor.executor.KotlincInvokeStatus
import com.stepanov.bbf.reduktor.util.MsgCollector
import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

open class JVMCompiler(override val arguments: String = "") : CommonCompiler() {
    override val compilerInfo: String
        get() = "JVM $arguments"

    override var pathToCompiled: String = "tmp/tmp.jar"


    override fun checkCompiling(project: Project): Boolean {
        val status = tryToCompile(project)
        return !MsgCollector.hasCompileError && !status.hasTimeout && !MsgCollector.hasException
    }

    override fun getErrorMessageWithLocation(project: Project): Pair<String, List<CompilerMessageSourceLocation>> {
        val status = tryToCompile(project)
        return status.combinedOutput to status.locations
    }

    override fun isCompilerBug(project: Project): Boolean =
        tryToCompile(project).hasException

    override fun compile(project: Project, numberOfExecutions: Int, includeRuntime: Boolean): CompilationResult {
        val projectWithMainFun = project.addMainAndExecBoxNTimes(numberOfExecutions)
        return getCompilationResult(projectWithMainFun, includeRuntime)
    }

/*
    fun compileJmh(project: Project, includeRuntime: Boolean = true): CompilationResult {
        val projectWithMainFun = project.addJmhMain()
        val compilationDir = "tmp/benchmark_tmp"
        // First, regular compile
        val args = prepareArgs(projectWithMainFun, projectWithMainFun.saveOrRemoveToTmp(true), compilationDir)
        val regularStatus = executeCompiler(projectWithMainFun, args)
        if (regularStatus.hasException || regularStatus.hasTimeout || !regularStatus.isCompileSuccess) return CompilationResult(
            -1,
            ""
        )
        // Then, generate jmh benchmark code with jmh bytecode generator
        commonExec("java -classpath ${classpath()} org.openjdk.jmh.generators.bytecode.JmhBytecodeGenerator $compilationDir $compilationDir $compilationDir")
        // Finally, compile the generated jmh benchmark code and assemble a jar
        val compilationUnits = File(compilationDir).walkTopDown().toList().filter { it.extension == "java" }.map { it.path }
        val javacStatus = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath",  "$compilationDir:${classpath()}", *compilationUnits.toTypedArray())
        if (javacStatus != 0) return CompilationResult(-1, "")
        commonExec("jar cf $pathToCompiled -C $compilationDir .")
        return CompilationResult(0, pathToCompiled)
    }
*/

    override fun compile(project: Project, includeRuntime: Boolean): CompilationResult {
        val projectWithMainFun = project.addMain()
        return getCompilationResult(projectWithMainFun, includeRuntime)
    }

    private fun getCompilationResult(projectWithMainFun: Project, includeRuntime: Boolean): CompilationResult {
        val path = projectWithMainFun.saveOrRemoveToTmp(true)
        val tmpJar = "$pathToCompiled.jar"
        val args = prepareArgs(projectWithMainFun, path, tmpJar)
        val status = executeCompiler(projectWithMainFun, args)
        if (status.hasException || status.hasTimeout || !status.isCompileSuccess) {
            logger.info("JVMCompiler failed to compile Kotlin files: ${projectWithMainFun.files.map {it.name}}\n Error: ${status.combinedOutput}")
            return CompilationResult(-1, "")
        }
        val res = File(pathToCompiled)
        val input = JarInputStream(File(tmpJar).inputStream())
        val output = JarOutputStream(res.outputStream(), input.manifest)
        copyFullJarImpl(output, File(tmpJar))
        if (includeRuntime)
            CompilerArgs.jvmStdLibPaths.forEach { writeRuntimeToJar(it, output) }
        output.finish()
        input.close()
        File(tmpJar).delete()
        return CompilationResult(0, pathToCompiled)
    }

    private fun prepareArgs(project: Project, path: String, destination: String): K2JVMCompilerArguments {
        val destFile = File(destination)
        if (destFile.isFile) destFile.delete()
        else if (destFile.isDirectory) FileUtils.cleanDirectory(destFile)
        val projectArgs = project.getProjectSettingsAsCompilerArgs("JVM") as K2JVMCompilerArguments
        val compilerArgs =
            if (arguments.isEmpty())
                "$path -d $destination".split(" ")
            else
                "$path $arguments -d $destination".split(" ")
        projectArgs.apply { K2JVMCompiler().parseArguments(compilerArgs.toTypedArray(), this) }
        //projectArgs.compileJava = true
        projectArgs.classpath = classpath()
        projectArgs.jvmTarget = "1.8"
        projectArgs.optIn = arrayOf("kotlin.ExperimentalStdlibApi", "kotlin.contracts.ExperimentalContracts")
        if (project.configuration.jvmDefault.isNotEmpty())
            projectArgs.jvmDefault = project.configuration.jvmDefault.substringAfter(Directives.jvmDefault)
        //TODO!!
        //if (project.configuration.samConversion.isNotEmpty()) {
        //val samConvType = project.configuration.samConversion.substringAfterLast(": ")
        //projectArgs.samConversions = samConvType.toLowerCase()
        //}
        return projectArgs
    }

    fun classpath() = "${
        CompilerArgs.jvmStdLibPaths.joinToString(
            separator = ":"
        )
    }:${System.getProperty("java.class.path")}"
        .split(":")
        .filter { it.isNotEmpty() }
        .toSet().toList()
        .joinToString(":")

    private fun executeCompiler(project: Project, args: K2JVMCompilerArguments): KotlincInvokeStatus {
        val compiler = K2JVMCompiler()
        val services = Services.EMPTY
        MsgCollector.clear()
        val threadPool = Executors.newCachedThreadPool()
        if (CompilerArgs.isInstrumentationMode) {
            CompilerInstrumentation.clearRecords()
            CompilerInstrumentation.shouldProbesBeRecorded = true
        } else {
            CompilerInstrumentation.shouldProbesBeRecorded = false
        }
        val futureExitCode = threadPool.submit {
            compiler.exec(MsgCollector, services, args)
        }
        var hasTimeout = false
        var compilerWorkingTime: Long = -1
        try {
            val startTime = System.currentTimeMillis()
            futureExitCode.get(10L, TimeUnit.SECONDS)
            compilerWorkingTime = System.currentTimeMillis() - startTime
        } catch (ex: TimeoutException) {
            hasTimeout = true
            futureExitCode.cancel(true)
        } finally {
            project.saveOrRemoveToTmp(false)
        }
        if (CompilerArgs.isInstrumentationMode) {
            CompilerInstrumentation.shouldProbesBeRecorded = false
        }
        val status = KotlincInvokeStatus(
            MsgCollector.crashMessages.joinToString("\n") +
                    MsgCollector.compileErrorMessages.joinToString("\n"),
            !MsgCollector.hasCompileError,
            MsgCollector.hasException,
            hasTimeout,
            compilerWorkingTime,
            MsgCollector.locations.toMutableList()
        )
        //println(status.combinedOutput)
        return status
    }

    override fun tryToCompile(project: Project): KotlincInvokeStatus {
        val path = project.saveOrRemoveToTmp(true)
        val trashDir = "${CompilerArgs.pathToTmpDir}/trash/"
        val args = prepareArgs(project, path, trashDir)
        return executeCompiler(project, args)
    }

    override fun exec(path: String, streamType: Stream, mainClass: String): String {
        val mc =
            mainClass.ifEmpty { JarInputStream(File(path).inputStream()).manifest.mainAttributes.getValue("Main-class") }
        return commonExec(
            "java -classpath ${classpath()}:$path $mc",
            streamType
        )
    }
    //commonExec("java -classpath ${CompilerArgs.jvmStdLibPaths.joinToString(":")} -jar $path", streamType)

    private fun analyzeErrorMessage(msg: String): Boolean = !msg.split("\n").any { it.contains(": error:") }

    private val log = Logger.getLogger("compilerErrorsLog")
}