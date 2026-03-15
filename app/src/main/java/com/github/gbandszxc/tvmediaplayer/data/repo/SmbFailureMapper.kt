package com.github.gbandszxc.tvmediaplayer.data.repo

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbFailure
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbRepositoryException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException

object SmbFailureMapper {
    fun map(throwable: Throwable): SmbFailure {
        if (throwable is SmbRepositoryException) return throwable.failure

        if (throwable is SocketTimeoutException) {
            return SmbFailure.TIMEOUT
        }
        if (throwable is UnknownHostException || throwable is ConnectException || throwable is NoRouteToHostException) {
            return SmbFailure.HOST_UNREACHABLE
        }
        if (throwable is SmbAuthException) {
            return SmbFailure.AUTH_FAILED
        }
        if (throwable is SmbException) {
            val message = throwable.message.orEmpty().lowercase()
            if ("logon failure" in message || "access denied" in message) {
                return SmbFailure.AUTH_FAILED
            }
            if ("bad network name" in message || "network name cannot be found" in message) {
                return SmbFailure.SHARE_NOT_FOUND
            }
            if ("object path not found" in message || "path not found" in message) {
                return SmbFailure.INVALID_PATH
            }
            if ("timeout" in message) {
                return SmbFailure.TIMEOUT
            }
            if ("host" in message && "unreachable" in message) {
                return SmbFailure.HOST_UNREACHABLE
            }
        }

        val nested = throwable.cause
        return if (nested != null && nested !== throwable) map(nested) else SmbFailure.UNKNOWN
    }

    fun toUserMessage(failure: SmbFailure): String =
        when (failure) {
            SmbFailure.AUTH_FAILED -> "SMB 认证失败，请检查用户名和密码"
            SmbFailure.HOST_UNREACHABLE -> "服务器不可达，请检查网络或主机地址"
            SmbFailure.SHARE_NOT_FOUND -> "共享名不存在，请确认 NAS 共享配置"
            SmbFailure.INVALID_PATH -> "路径无效，请检查子目录配置"
            SmbFailure.TIMEOUT -> "连接超时，请稍后重试"
            SmbFailure.UNKNOWN -> "SMB 浏览失败，请检查配置后重试"
        }
}

