plugins {
    id("com.android.application")
}

dependencies {
    implementation("com.parodison:orbit-core:0.1.0")
    // Atribuição oficial de instalações da Google Play.
    implementation("com.android.installreferrer:installreferrer:2.2")
}

android {
    namespace = "br.com.observatorioespacial"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.observatorioespacial"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"

        // ABIs oficiais suportadas pelo Android NDK.
        // Inclui ARM 32/64 bits e x86 32/64 bits.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O2")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
