// WITH_RUNTIME
// WITH_COROUTINES
import helpers.*

import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

suspend fun suspendThere(v: A): A = suspendCoroutineUninterceptedOrReturn { x ->
    x.resume(v)
    COROUTINE_SUSPENDED
}

class A(var value: String) {
    operator suspend fun plus(other: A) = suspendThere(A(value + other.value))
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

suspend fun usePlusAssign(): A {
    var a = A("O")
    a += A("K")
    return a
}

fun box(): String {
    var a = A("")
    builder { a = usePlusAssign() }
    return a.value
}
