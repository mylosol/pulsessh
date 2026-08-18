/*
 * PulseSSH :app module.
 *
 * Two decisions in this file are worth explaining up front, because they are the
 * ones most likely to surprise someone reading it later:
 *
 * 1. Release signing is opt-in and environment driven.
 *    The release keystore never lives in the repository. The four values needed to
 *    sign (PULSESSH_KEYSTORE_PATH, PULSESSH_KEYSTORE_PASSWORD, PULSESSH_KEY_ALIAS,
 *    PULSESSH_KEY_PASSWORD) are read from the environment through Gradle's
 *    `providers.environmentVariable(...)` API, which keeps the configuration cache
 *    correct: Gradle records the values as build inputs and re-configures only when
 *    they change. The signing config is *created* and wired into the release build
 *    type only when all four are present. On a developer machine or on a pull
 *    request build none of them are set, so the release variant simply builds
 *    unsigned instead of failing configuration. Only the tag/release workflow, which
 *    injects the secrets, produces a signed artifact.
 *
 * 2. Room schemas are exported and shipped to androidTest.
 *    `room.schemaLocation` points at app/schemas, and those JSON files are committed
 *    so that every schema change is visible in review and so migrations can be
 *    verified. `MigrationTestHelper` reads the schemas from the *assets* of the test
 *    APK, therefore the schemas directory is registered as an androidTest asset
 *    source directory below. Without that line migration tests fail at runtime with
 *    "Cannot find the schema file in the assets folder".
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

/**
 * Release signing material, supplied by CI through the environment.
 * `orNull` here means "absent locally" and must never be treated as an error.
 */
val keystorePath: String? = providers.environmentVariable("PULSESSH_KEYSTORE_PATH").orNull
val keystorePassword: String? = providers.environmentVariable("PULSESSH_KEYSTORE_PASSWORD").orNull
val keyAliasName: String? = providers.environmentVariable("PULSESSH_KEY_ALIAS").orNull
val keyPasswordValue: String? = providers.environmentVariable("PULSESSH_KEY_PASSWORD").orNull

/** True only when the full set of signing inputs is available. */
val hasReleaseSigning: Boolean =
    listOf(keystorePath, keystorePassword, keyAliasName, keyPasswordValue)
        .all { !it.isNullOrBlank() }

android {
    namespace = "com.pulsessh.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pulsessh.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // A Hilt-aware runner (com.pulsessh.app.HiltTestRunner) would be preferable for
        // instrumented tests, but no such class exists in the source set yet and naming a
        // missing runner breaks every androidTest task. Switch to it once it is written.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Apache MINA SSHD and BouncyCastle are plain JVM artifacts and ship service
    // metadata and licence files that collide once several of them land in one APK.
    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/INDEX.LIST",
                    "META-INF/*.kotlin_module",
                    "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                )
        }
    }

    sourceSets {
        // See note (2) at the top of the file: migration tests read schemas from assets.
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
        // The same schemas are exposed to the JVM test source set so that a Robolectric
        // backed MigrationTestHelper can run on a pull request without an emulator job.
        getByName("test") {
            resources.srcDirs(files("$projectDir/schemas"))
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.sshd.core)
    implementation(libs.sshd.common)
    implementation(libs.sshd.sftp)
    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.commons.net)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
