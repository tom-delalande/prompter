import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("androidApp/keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "co.delalande.prompter"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.delalande.prompter"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("versionCode")?.toString()
            ?: throw GradleException("versionCode not set. Pass -PversionCode=<N> or use Fastlane.")).toInt()
        versionName = project.findProperty("versionName")?.toString()
            ?: throw GradleException("versionName not set. Pass -PversionName=<N> or use Fastlane.")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile") ?: "keystore/release.keystore")
            storePassword = keystoreProperties.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD").orEmpty()
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: "teleprompter"
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD").orEmpty()
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    dependencies {
        implementation(project(":composeApp"))
        implementation("androidx.activity:activity-compose:1.10.1")
    }
}
