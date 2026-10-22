plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Android/Compose-only files; everything else in app/src/main is compiled here.
val androidOnly = listOf(
    "**/*Screen.kt",
    "**/*Tab.kt",
    "**/market/InsightCards.kt",
    "**/MainActivity.kt",
    "**/CriptoPanelApp.kt",
    "**/navigation/**",
    "**/core/ui/LineChart.kt",
    "**/core/ui/AutoRefresh.kt",
    "**/core/ui/StatusViews.kt",
    "**/core/ui/Theme.kt",
    "**/core/ui/Glass.kt",
    "**/core/ui/Scaffolds.kt",
)

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.srcDir("src/shim/kotlin")
        kotlin.exclude(androidOnly)
    }
    test {
        kotlin.srcDir("../app/src/test/java")
        resources.srcDir("../app/src/test/resources")
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}

tasks.test { testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
