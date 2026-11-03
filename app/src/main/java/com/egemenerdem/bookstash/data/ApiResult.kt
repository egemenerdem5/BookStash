package com.egemenerdem.bookstash.data

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Exception, val message: String) : ApiResult<Nothing>()

}

