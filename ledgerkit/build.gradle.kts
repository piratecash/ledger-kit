import com.android.build.gradle.tasks.BundleAar
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    // jvmCommon: blocking HID and socket I/O shared by both JVM targets, so commonMain stays pure.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.hid4java)
                // hid4java asks for an open JNA range; pin it so builds stay reproducible.
                implementation(libs.jna)
            }
        }
    }
}

android {
    namespace = "cash.p.terminal.ledgerkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        targetSdk = 33
    }
    testOptions {
        targetSdk = 33
    }
}

// Adapted Apache-2.0 code must ship with its license and notices; a unique path avoids clashes in consumer packages.
val legalFiles = rootProject.files("LICENSE", "LICENSE-APACHE", "NOTICE")
tasks.withType<Jar>().configureEach { from(legalFiles) { into("META-INF/ledger-kit") } }
tasks.withType<BundleAar>().configureEach { from(legalFiles) { into("META-INF/ledger-kit") } }
