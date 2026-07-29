plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.teleprompter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.teleprompter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    dependencies {
        implementation(project(":composeApp"))
        implementation("androidx.activity:activity-compose:1.10.1")
    }
}
