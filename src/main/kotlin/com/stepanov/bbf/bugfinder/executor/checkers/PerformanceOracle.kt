package com.stepanov.bbf.bugfinder.executor.checkers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.compilers.KJCompiler
import com.stepanov.bbf.bugfinder.executor.project.Project
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.PumpStreamHandler
import java.io.ByteArrayOutputStream


object PerformanceOracle {
    lateinit var project: Project
    lateinit var compilers: List<CommonCompiler>
    lateinit var compilationConfInterval: Pair<Double, Double>
    var compilationSigma: Double = -1.0
    lateinit var executionConfInterval: Pair<Double, Double>
    var executionSigma: Double = -1.0

    fun profileProject(project: Project, compilers: List<JVMCompiler>): Pair<Double, Double>? {
       println("EXECUTUION INITIALIZATION")

        val executionTimes = compilers.map { compiler ->
            // JMH generates java comde, so
            val kjCompiler = if (compiler !is KJCompiler) KJCompiler(compiler.arguments) else compiler
           val compilationResult = kjCompiler.compileJmh(project, false)
           if (compilationResult.status != 0) throw Exception("Project ${project.files} does not compile with ${compiler.arguments}")
            val outputStream = ByteArrayOutputStream()
            val executor = DefaultExecutor().also {
                it.streamHandler = PumpStreamHandler(outputStream)
            }
            executor.streamHandler.stop()
            // TODO?: Replace /dev/null, /dev/stdout with something platform independent?
            val commandLine = CommandLine.parse("java -cp ${compilationResult.pathToCompiled}:${compiler.classpath()} org.openjdk.jmh.Main -r 1 -w 1 -f 1 -rf json -rff /dev/stdout -o /dev/null -tu ns -bm avgt")
            val execResult = executor.execute(commandLine)
            if (execResult != 0) throw Exception("Project ${project.files}, JMH fail")
            println(outputStream.toString())
        }

        return null
    }
}