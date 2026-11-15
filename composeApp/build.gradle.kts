import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val ktorVersion = "3.0.1"
        val coilVersion = "3.0.4"
        val media3Version = "1.3.1"

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

            implementation("io.ktor:ktor-client-core:$ktorVersion")

            implementation("io.coil-kt.coil3:coil-compose:$coilVersion")
            implementation("io.coil-kt.coil3:coil-network-ktor3:$coilVersion")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
            implementation("com.squareup.okhttp3:okhttp:4.12.0")

            implementation("androidx.media3:media3-exoplayer:$media3Version")
            implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
            implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
            implementation("androidx.media3:media3-ui:$media3Version")
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
            }
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
            implementation("io.ktor:ktor-server-core:$ktorVersion")
            implementation("io.ktor:ktor-server-cio:$ktorVersion")
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("io.ktor:ktor-server-core:$ktorVersion")
                implementation("io.ktor:ktor-server-cio:$ktorVersion")

                implementation("org.bytedeco:javacv:1.5.11") {
                    isTransitive = false
                }
                implementation("org.bytedeco:javacpp:1.5.11")
                implementation("org.bytedeco:javacpp:1.5.11:windows-x86_64")
                implementation("org.bytedeco:ffmpeg:7.1-1.5.11")
                implementation("org.bytedeco:ffmpeg:7.1-1.5.11:windows-x86_64")
            }
        }
    }
}

android {
    namespace = "com.phimbud.mobile"
    compileSdk = 34

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        applicationId = "com.phimbud.mobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.phimbud.mobile.MainKt"
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dskiko.renderApi=DIRECT3D",
            "-Dsun.java2d.d3d=true",
            "-Dsun.java2d.noddraw=true",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=10",
            "-XX:+ExplicitGCInvokesConcurrent"
        )

        nativeDistributions {
            outputBaseDir.set(project.layout.buildDirectory.dir("release-windows-v1.1.8"))
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "PhimBud"
            packageVersion = "1.1.8"
            description = "PhimBud Windows App"
            vendor = "PhimBud"
            modules(
                "java.desktop",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.prefs",
                "java.sql",
                "jdk.crypto.ec",
                "jdk.unsupported"
            )
            windows {
                menuGroup = "PhimBud"
                shortcut = true
                dirChooser = true
                upgradeUuid = "d306cf30-a001-3822-8129-e8d6c5539794"
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
        }
    }
}
