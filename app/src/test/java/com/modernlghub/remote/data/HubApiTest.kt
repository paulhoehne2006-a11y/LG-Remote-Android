package com.modernlghub.remote.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * HubApi against a real local HTTP/1.0 server (like the hub's http.server), covering the
 * transport edge cases. A minimal socket server, because the JDK's HttpServer is not on the
 * Android unit-test compile classpath.
 */
class HubApiTest {

    private class Route(val code: Int, val body: String, val hang: Boolean = false)

    private lateinit var server: ServerSocket
    private val release = CountDownLatch(1)
    private val lastToken = AtomicReference<String?>()
    private var token = ""

    private val routes = mapOf(
        "/api/status" to Route(200, """{"tv": {"power": "Active"}}"""),
        "/api/quick" to Route(502, """{"action": "screen_off", "success": false}"""),
        "/api/key" to Route(400, """{"success": false, "error": "Ungültiges Key-Format"}"""),
        "/api/slow" to Route(200, "{}", hang = true),
    )

    private val base get() = "http://127.0.0.1:${server.localPort}"
    private val api by lazy { HubApi(baseUrl = { base }, token = { token }) }

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { serve(socket) } }
            }
        }
    }

    private fun serve(socket: Socket) = socket.use { s ->
        val input = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val path = input.readLine()?.split(' ')?.getOrNull(1) ?: return
        var length = 0
        var tokenHeader: String? = null
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val name = line.substringBefore(':').trim()
            val value = line.substringAfter(':').trim()
            if (name.equals("Content-Length", true)) length = value.toInt()
            if (name.equals(HubApi.TOKEN_HEADER, true)) tokenHeader = value
        }
        repeat(length) { input.read() }
        lastToken.set(tokenHeader)
        val route = routes[path] ?: Route(404, "")
        if (route.hang) release.await(10, TimeUnit.SECONDS)
        val body = route.body.toByteArray()
        val out = s.getOutputStream()
        out.write("HTTP/1.0 ${route.code} X\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    @After
    fun tearDown() {
        release.countDown()
        server.close()
    }

    @Test
    fun `ok, rejected with message and plain status codes`() = runBlocking {
        val ok = api.get("/api/status")
        assertTrue(ok is HubResult.Ok)
        assertEquals("Active", TvStatus.parse((ok as HubResult.Ok).body).power)
        assertEquals(HubResult.Rejected(502, "HTTP 502"), api.post("/api/quick"))
        assertEquals(HubResult.Rejected(400, "Ungültiges Key-Format"), api.post("/api/key"))
        assertEquals(HubResult.Rejected(404, "HTTP 404"), api.get("/api/missing"))
    }

    @Test
    fun `token header only when configured`() = runBlocking {
        api.get("/api/status")
        assertEquals(null, lastToken.get())
        token = "s3cret"
        api.get("/api/status")
        assertEquals("s3cret", lastToken.get())
    }

    @Test
    fun `closed port and broken urls are unreachable, not exceptions`() = runBlocking {
        assertEquals(HubResult.Unreachable, api.get("/api", 1_000, base = "http://127.0.0.1:1"))
        assertEquals(HubResult.Unreachable, api.get("/api", 1_000, base = "not a url"))
        assertEquals(HubResult.Unreachable, api.get("/api", 1_000, base = ""))
    }

    @Test
    fun `back-to-back requests to an HTTP 1_0 server that closes every connection all succeed`() = runBlocking {
        // Without "Connection: close" OkHttp pools these sockets and a later call can hit a dead one.
        val results = (1..30).map { api.get("/api/status", 2_000) }
        assertTrue(results.all { it is HubResult.Ok })
        val posts = (1..10).map { api.post("/api/quick", timeoutMs = 2_000) }
        assertTrue(posts.all { it == HubResult.Rejected(502, "HTTP 502") })
    }

    @Test
    fun `a malformed address from the settings does not throw`() = runBlocking {
        val broken = HubApi(baseUrl = { HubApi.normalizeBaseUrl("192.168.1.5 8080") })
        assertEquals(HubResult.Unreachable, broken.get("/api/status", 500))
        assertEquals(HubResult.Unreachable, broken.post("/api/quick", timeoutMs = 500))
    }

    @Test
    fun `deadline is enforced per call`() = runBlocking {
        val started = System.nanoTime()
        assertEquals(HubResult.Unreachable, api.get("/api/slow", 300))
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $tookMs ms", tookMs < 2_000)
    }

    @Test
    fun `cancelling the caller cancels the request at once`() = runBlocking {
        val call = async { api.get("/api/slow", 10_000) }
        delay(200)
        val started = System.nanoTime()
        call.cancel()
        call.join()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("cancel took $tookMs ms", tookMs < 1_000)
        assertTrue(call.isCancelled)
    }
}
