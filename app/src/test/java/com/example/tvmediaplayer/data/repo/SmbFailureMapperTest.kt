package com.example.tvmediaplayer.data.repo

import com.example.tvmediaplayer.domain.model.SmbFailure
import com.example.tvmediaplayer.domain.model.SmbRepositoryException
import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

class SmbFailureMapperTest {

    @Test
    fun `maps repository exception directly`() {
        val throwable = SmbRepositoryException(SmbFailure.SHARE_NOT_FOUND)
        assertEquals(SmbFailure.SHARE_NOT_FOUND, SmbFailureMapper.map(throwable))
    }

    @Test
    fun `maps connect exception to unreachable`() {
        assertEquals(SmbFailure.HOST_UNREACHABLE, SmbFailureMapper.map(ConnectException("refused")))
    }

    @Test
    fun `maps timeout exception to timeout`() {
        assertEquals(SmbFailure.TIMEOUT, SmbFailureMapper.map(SocketTimeoutException("timeout")))
    }

    @Test
    fun `returns unknown for unsupported errors`() {
        assertEquals(SmbFailure.UNKNOWN, SmbFailureMapper.map(IllegalStateException("x")))
    }
}

