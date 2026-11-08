import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.asdfghasdfgh.norikae"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.asdfghasdfgh.norikae"
        minSdk = 28
        targetSdk = 35
        // versionCode packs the release tag: major * 10_000_000 + minor * 10_000 + patch.
        // v0.1.1 -> 10001.
        versionCode = 10_001
        versionName = "0.1.1"
        // MapLibre ships ~15 MB of native code per ABI; the Kompakt is arm64
        ndk { abiFilters += "arm64-v8a" }
    }

    // A real keystore in signing/ signs every build type when it is present, so the
    // very first install is already release-signed and a later update can never hit
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE. It is gitignored, and there is no fallback:
    // a fresh clone builds an unsigned release APK, which will not install anywhere.
    val signingPropertiesFile = rootProject.file("signing/signing.properties")
    val realSigningConfig = if (signingPropertiesFile.isFile) {
        val signingProperties = Properties().apply {
            signingPropertiesFile.inputStream().use(::load)
        }
        signingConfigs.create("real") {
            storeFile = rootProject.file("signing/signing.keystore")
            storePassword = signingProperties.getProperty("STORE_PASSWORD")
            keyAlias = signingProperties.getProperty("KEY_ALIAS")
            keyPassword = signingProperties.getProperty("KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            realSigningConfig?.let { signingConfig = it }
        }
        getByName("release") {
            isMinifyEnabled = false
            realSigningConfig?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // AGP stamps the git revision into META-INF; keep it out so the same
            // version built in different places has the same contents.
            vcsInfo {
                include = false
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

    // Compress native libs: AGP does not 16 KB-align uncompressed .so files,
    // which makes newer Android builds show a compatibility dialog on
    // debuggable apps. Compressed libs are exempt from the check.
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME feeds the Transitous User-Agent
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Mudita Mindful Design Framework
    implementation(libs.mmd)

    // Walk map: open source GL map renderer, monochrome style over OpenFreeMap tiles
    implementation(libs.maplibre)

    testImplementation(libs.junit)
    debugImplementation(libs.compose.ui.tooling)
}
