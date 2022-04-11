package com.stepanov.bbf.bugfinder.executor

import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.SingleFileBugFinder
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.addAtTheEnd
import com.stepanov.bbf.bugfinder.util.addImport
import com.stepanov.bbf.bugfinder.util.addToTheTop
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.lang.StringBuilder
import org.openjdk.jmh.annotations.Benchmark

fun PsiFile.addMain(boxFuncs: List<KtNamedFunction>) {
    val m = StringBuilder()
    m.append("fun main(args: Array<String>) {\n")
    for (func in boxFuncs) m.append("println(${func.name}())\n")
    m.append("}")
    val mainFun = KtPsiFactory(this.project).createFunction(m.toString())
    this.add(KtPsiFactory(this.project).createWhiteSpace("\n\n"))
    this.add(mainFun)
}

fun PsiFile.addMainForPerformanceTesting(boxFuncs: List<KtNamedFunction>, times: Int) {
    val m = StringBuilder()
    m.append("fun main(args: Array<String>) {\n")
    for (func in boxFuncs) {
        m.append(
            """
        repeat($times) { ${func.name}() }
        """.trimIndent()
        )
        m.append("\n")
    }
    m.append("}")
    val mainFun = KtPsiFactory(this.project).createFunction(m.toString())
    this.add(KtPsiFactory(this.project).createWhiteSpace("\n\n"))
    this.add(mainFun)
}

fun KtFile.addJmhMain(boxFuncs: List<KtNamedFunction>) {
    if (boxFuncs.size != 1) throw IllegalStateException("Assuming only one box fun per project for now")
    val boxFun = boxFuncs.first()
    this.addImport("org.openjdk.jmh.annotations", true)

    this.addToTheTop(KtPsiFactory(this.project).createPackageDirective(FqName("benchmark")))
    val benchClass = """\n
        open class MyBench {
            @Benchmark
            fun benchmark() = box()
        }
    """.trimIndent()
    this.add(KtPsiFactory(this.project).createWhiteSpace("\n\n"))
    this.addAtTheEnd(KtPsiFactory(this.project).createClass(benchClass))
}