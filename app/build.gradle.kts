plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nova.vpn.android"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    testBuildType = if (providers.gradleProperty("novaMapSmoke").isPresent) "maptest" else "debug"

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    defaultConfig {
        applicationId = "com.nova.vpn.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 46
        versionName = "0.2.44"
        testInstrumentationRunner = if (providers.gradleProperty("novaMapSmoke").isPresent)
            "com.nova.vpn.android.ServerMapSmokeRunner" else "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs.getByName("debug") {
        // Keep private signing files outside the public repository.
        val suppliedKey = providers.gradleProperty("novaSigningKeystore").orNull?.let { file(it) }
        val previewKey = rootProject.file("signing/debug.keystore")
        if (suppliedKey != null) {
            require(suppliedKey.isFile) { "Specified signing keystore does not exist" }
            storeFile = suppliedKey
        } else if (previewKey.isFile) storeFile = previewKey
    }

    buildTypes {
        create("designreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".designreview"
            versionNameSuffix = "-design-preview"
            matchingFallbacks += listOf("debug")
        }
        create("optimized") {
            initWith(getByName("release"))
            isDebuggable = false
            isJniDebuggable = false
            // The handoff does not include the original signing key. Distribute designpreview separately.
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("designpreview") {
            initWith(getByName("optimized"))
            applicationIdSuffix = ".designpreview"
            versionNameSuffix = "-design"
            matchingFallbacks += listOf("optimized", "release")
        }
        create("maptest") {
            initWith(getByName("optimized"))
            applicationIdSuffix = ".maptest"
            matchingFallbacks += listOf("optimized", "release")
        }
    }

    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        val testHome = layout.buildDirectory.dir("test-home").get().asFile
        it.systemProperty("user.home", testHome.absolutePath)
        it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        it.doFirst { testHome.mkdirs() }
    }

}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("org.json:json:20240303")

    // Android-native sing-box core (VPNService binding); not the Windows executable.
    implementation("com.github.singbox-android:libbox:1.14.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

