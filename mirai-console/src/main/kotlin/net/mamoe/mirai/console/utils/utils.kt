@file: JvmName("Utils")
@file:JvmMultifileClass

package net.mamoe.mirai.console.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * 执行 N 次 [block]
 * 在第一次成功 (无异常抛出) 时返回
 */
@OptIn(ExperimentalContracts::class)
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "RESULT_CLASS_IN_RETURN_TYPE")
@kotlin.internal.InlineOnly
inline fun <R> retryCatching(n: Int, block: () -> R): Result<R> {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    require(n >= 0) { "param n for retryCatching must not be negative" }
    var exception: Throwable? = null
    repeat(n) {
        try {
            return Result.success(block())
        } catch (e: Throwable) {
            exception?.addSuppressedMirai(e)
            exception = e
        }
    }
    return Result.failure(exception!!)
}

@OptIn(ExperimentalContracts::class)
inline fun <T> tryNTimes(n: Int = 2, block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    require(n >= 0) { "param n for tryNTimes must not be negative" }
    var last: Exception? = null
    repeat(n) {
        try {
            return block.invoke()
        } catch (e: Exception) {
            last = e
        }
    }

    //给我编译

    throw last!!
}

@PublishedApi
internal fun Throwable.addSuppressedMirai(e: Throwable) {
    if (e === this) {
        return
    }
    kotlin.runCatching {
        this.addSuppressed(e)
    }
}
