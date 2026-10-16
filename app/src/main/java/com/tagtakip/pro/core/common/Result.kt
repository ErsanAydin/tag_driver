package com.tagtakip.pro.core.common

import kotlinx.coroutines.CancellationException

/**
 * Domain ve presentation katmanları arasında kullanılan sonuç sarmalayıcısı.
 * Not: kotlin.Result ile çakışmaması için açıkça import edilmelidir.
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(
        val exception: Throwable,
        val message: String? = exception.message
    ) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        Loading -> Loading
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Error) -> Unit): Result<T> {
        if (this is Error) action(this)
        return this
    }
}

/** Bloğu çalıştırır, istisnaları Result.Error'a çevirir (coroutine iptalini yutmaz). */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.Error(e)
}
