import java.security.MessageDigest
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nova.vpn.android"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    testBuildType = when {
        providers.gradleProperty("novaIdentitySmoke").isPresent -> "identitypreview"
        providers.gradleProperty("novaRealitySmoke").isPresent -> "realitypreview"
        providers.gradleProperty("novaMapSmoke").isPresent -> "maptest"
        else -> "debug"
    }

    splits {
        abi {
            isEnable = true
            reset()
            val smokeAbi = providers.gradleProperty("novaRealityAbi").orNull
            require(smokeAbi == null || (providers.gradleProperty("novaRealitySmoke").isPresent &&
                smokeAbi in setOf("arm64-v8a", "armeabi-v7a", "x86_64"))) { "novaRealityAbi is restricted to native smoke tests" }
            if (smokeAbi != null) include(smokeAbi) else include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = smokeAbi == null
        }
    }

    defaultConfig {
        applicationId = "com.nova.vpn.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 54
        versionName = "0.2.52"
        manifestPlaceholders["novaAppLabel"] = "NOVA VPN"
        buildConfigField("boolean", "REALITY_PREVIEW", "false")
        testInstrumentationRunner = when {
            providers.gradleProperty("novaIdentitySmoke").isPresent -> "com.nova.vpn.android.IdentitySmokeRunner"
            providers.gradleProperty("novaRealitySmoke").isPresent -> "com.nova.vpn.android.RealityCoreSmokeRunner"
            providers.gradleProperty("novaMapSmoke").isPresent -> "com.nova.vpn.android.ServerMapSmokeRunner"
            else -> "androidx.test.runner.AndroidJUnitRunner"
        }
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
            // Production publication supplies the original external NOVA signing key.
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("designpreview") {
            initWith(getByName("optimized"))
            applicationIdSuffix = ".designpreview"
            versionNameSuffix = "-design"
            matchingFallbacks += listOf("optimized", "release")
        }
        create("realitypreview") {
            initWith(getByName("optimized"))
            applicationIdSuffix = if (providers.gradleProperty("novaUsbTest").isPresent)
                ".usbtest.realitypreview" else ".realitypreview"
            versionNameSuffix = "-reality-preview.2"
            manifestPlaceholders["novaAppLabel"] = if (providers.gradleProperty("novaUsbTest").isPresent)
                "NOVA VPN · USB Test" else "NOVA VPN · Reality Test"
            buildConfigField("boolean", "REALITY_PREVIEW", "true")
            matchingFallbacks += listOf("optimized", "release")
        }
        create("identitypreview") {
            initWith(getByName("realitypreview"))
            applicationIdSuffix = ".identitypreview"
            versionNameSuffix = "-identity-preview.1"
            manifestPlaceholders["novaAppLabel"] = "NOVA · Identity Test"
            matchingFallbacks += listOf("realitypreview", "optimized", "release")
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

// A pinned downstream is necessary: upstream libbox does not implement XHTTP.
val xhttpCore = layout.projectDirectory.file("libs/libbox-xhttp.aar")
val prepareXhttpCore = tasks.register("prepareXhttpCore") {
    outputs.file(xhttpCore)
    outputs.upToDateWhen { false } // Verify cached bytes as well as downloads.
    doLast {
        val target = xhttpCore.asFile
        if (!target.exists()) {
            check(!gradle.startParameter.isOffline) { "XHTTP core missing; run :app:prepareXhttpCore once without --offline" }
            target.parentFile.mkdirs()
            val temporary = File(target.path + ".download")
            try {
                URI("https://github.com/Leadaxe/sing-box-lx/releases/download/v1.14.3-lx.23/libbox-1.14.3-lx.23.aar").toURL().openStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
                temporary.copyTo(target, overwrite = false)
            } finally { temporary.delete() }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        check(digest.digest().joinToString("") { "%02x".format(it) } == "d7be8b1297f68ad8bad47e5eb766f59501100b1e9377de403d9c53cd364ecef1") { "XHTTP core checksum mismatch" }
    }
}

dependencies {
    implementation(files("libs/zxing-core-3.5.4.jar"))
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
    implementation(files(xhttpCore).builtBy(prepareXhttpCore))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


