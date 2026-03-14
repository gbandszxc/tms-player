package com.example.tvmediaplayer.domain.model

enum class SmbFailure {
    AUTH_FAILED,
    HOST_UNREACHABLE,
    SHARE_NOT_FOUND,
    INVALID_PATH,
    TIMEOUT,
    UNKNOWN
}

class SmbRepositoryException(
    val failure: SmbFailure,
    cause: Throwable? = null
) : RuntimeException(cause?.message ?: failure.name, cause)

