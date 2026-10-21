package app.criptopanel.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class OutcomeTest {
    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test fun `429 is RateLimited`() = runTest {
        assertEquals(Outcome.Err(AppError.RateLimited), apiCall { throw httpError(429) })
    }

    @Test fun `other http codes keep the code`() = runTest {
        assertEquals(Outcome.Err(AppError.Http(503)), apiCall { throw httpError(503) })
    }

    @Test fun `io is Offline`() = runTest {
        assertEquals(Outcome.Err(AppError.Offline), apiCall { throw IOException() })
    }

    @Test fun `ok wraps value`() = runTest {
        assertEquals(Outcome.Ok(1), apiCall { 1 })
    }

    private fun headerSent(client: OkHttpClient, server: MockWebServer): String? {
        server.enqueue(MockResponse())
        client.newCall(Request.Builder().url(server.url("/ping")).build()).execute().close()
        return server.takeRequest().getHeader("x-cg-demo-api-key")
    }

    @Test fun `demo key header only when set`() {
        val server = MockWebServer().apply { start() }
        assertNull(headerSent(okHttpClient(demoKey = "", coinGeckoHost = server.hostName), server))
        assertEquals("k1", headerSent(okHttpClient(demoKey = "k1", coinGeckoHost = server.hostName), server))
        assertNull(headerSent(okHttpClient(demoKey = "k1"), server))
        server.shutdown()
    }
}
