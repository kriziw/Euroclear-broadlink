package io.github.kriziw.bl3372setup.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalHttpTest {
    @Test
    fun `reads content-length, chunked and read-to-end bodies`() = runBlocking {
        for ((response, expected) in listOf(
            FakeHttpServer.ok("""{"data":"0600"}""") to """{"data":"0600"}""",
            FakeHttpServer.chunked("<data><D_A_1_1>0.3", "2</D_A_1_1></data>") to "<data><D_A_1_1>0.32</D_A_1_1></data>",
            FakeHttpServer.untilClose("""{"getRE1":395}""") to """{"getRE1":395}""",
        )) {
            FakeHttpServer { response }.use { server ->
                val reply = LocalHttp(server.connector).get("softener", server.port, "/x")
                assertEquals(200, reply.status)
                assertEquals(expected, reply.body)
            }
        }
    }

    @Test
    fun `sends basic auth, host and form bodies`() = runBlocking {
        FakeHttpServer { FakeHttpServer.ok("ok") }.use { server ->
            val http = LocalHttp(server.connector)
            http.get("judo.local", server.port, "/api/rest/FF00", LocalHttp.BasicAuth("admin", "Connectivity"))
            http.post("10.0.0.5", server.port, "/mux_http", "id=2444&show=D_Y_6~", "application/x-www-form-urlencoded")
            val (get, post) = server.requests
            assertEquals("Basic YWRtaW46Q29ubmVjdGl2aXR5", get.headers["authorization"])
            assertEquals("judo.local:${server.port}", get.headers["host"])
            assertEquals("POST", post.method)
            assertEquals("id=2444&show=D_Y_6~", post.body)
            assertFalse("authorization" in post.headers)
        }
    }

    @Test
    fun `basic auth never prints the password`() {
        assertFalse("Connectivity" in LocalHttp.BasicAuth("admin", "Connectivity").toString())
    }
}
