plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("io.github.takahirom.roborazzi") version "1.20.0"
}

android {
    namespace = "com.subhash.chatlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.subhash.chatlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.5.2"

        // Automatic AI enrollment (owner 14:43): the ticket is injected at build time from a
        // vault-materialized file PATH (-PenrollmentTicketFile=...) - never source, repo, logs,
        // or a command-line value. Empty in normal/dev builds = manual pairing link only.
        val enrollmentTicket = providers.gradleProperty("enrollmentTicketFile").orNull
            ?.let { path -> file(path).takeIf { it.exists() }?.readText()?.trim() }.orEmpty()
        val ticketEscaped = enrollmentTicket.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "ENROLLMENT_TICKET", "\"$ticketEscaped\"")
    }

    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.json:json:20231013")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
}
