pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
    repositories {
        google()
        if (providers.gradleProperty("novaOfficialRepositoriesOnly").orNull != "true") {
            maven("https://maven.aliyun.com/repository/google")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("vendor/maven"); content { includeGroup("com.github.singbox-android") } }
        google()
        if (providers.gradleProperty("novaOfficialRepositoriesOnly").orNull != "true") {
            maven("https://maven.aliyun.com/repository/google")
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "NOVA-VPN-Android"
include(":app")
