package com.mcgo.app.network

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlin.test.Test

class TcpLatencyProbeTest {

    @Test
    fun parseTcpEndpoint_acceptsDomainIpv4AndBracketIpv6WithPort() {
        assertThat(parseTcpEndpoint("frp.example.com:7000")).isEqualTo(TcpEndpoint("frp.example.com", 7000))
        assertThat(parseTcpEndpoint("192.168.1.2:8080")).isEqualTo(TcpEndpoint("192.168.1.2", 8080))
        assertThat(parseTcpEndpoint("[2001:db8::1]:443")).isEqualTo(TcpEndpoint("2001:db8::1", 443))
    }

    @Test
    fun parseTcpEndpoint_rejectsMissingOrInvalidPort() {
        assertThat(parseTcpEndpoint("frp.example.com")).isNull()
        assertThat(parseTcpEndpoint("frp.example.com:0")).isNull()
        assertThat(parseTcpEndpoint("frp.example.com:70000")).isNull()
        assertThat(parseTcpEndpoint("frp.example.com:not-a-port")).isNull()
    }

    @Test
    fun measureTcpLatency_returnsElapsedMillisecondsForSuccessfulConnect() {
        val timestamps = ArrayDeque(listOf(1_000_000_000L, 1_042_000_000L))
        val latency = measureTcpLatency(
            endpoint = TcpEndpoint("frp.example.com", 7000),
            timeoutMillis = 1000,
            connector = { endpoint, timeout ->
                assertThat(endpoint).isEqualTo(TcpEndpoint("frp.example.com", 7000))
                assertThat(timeout).isEqualTo(1000)
            },
            nanoTime = { timestamps.removeFirst() },
        )

        assertThat(latency).isEqualTo(42)
    }

    @Test
    fun measureTcpLatency_returnsNullWhenConnectFails() {
        val latency = measureTcpLatency(
            endpoint = TcpEndpoint("frp.example.com", 7000),
            connector = { _, _ -> throw IOException("timeout") },
            nanoTime = { 1_000_000_000L },
        )

        assertThat(latency).isNull()
    }
}
