package app.criptopanel

import app.criptopanel.core.network.okHttpClient
import app.criptopanel.core.network.retrofit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.classLoader.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

fun MockWebServer.enqueueFixture(name: String) = enqueue(MockResponse().setBody(fixture(name)))

inline fun <reified T> MockWebServer.api(): T =
    retrofit(url("/").toString(), okHttpClient(demoKey = "")).create(T::class.java)
