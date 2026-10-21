package app.criptopanel

import app.criptopanel.core.network.appJson
import app.criptopanel.core.network.okHttpClient
import app.criptopanel.core.network.retrofit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.TestWatcher
import org.junit.runner.Description

fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.classLoader.getResource("fixtures/$name")) { "missing fixture $name" }.readText()

inline fun <reified T> decodeFixture(name: String): T = appJson.decodeFromString(fixture(name))

fun MockWebServer.enqueueFixture(name: String) = enqueue(MockResponse().setBody(fixture(name)))

inline fun <reified T> MockWebServer.api(): T =
    retrofit(url("/").toString(), okHttpClient(demoKey = "", retrySleep = {})).create(T::class.java)

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
