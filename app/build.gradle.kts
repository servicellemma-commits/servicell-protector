plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 🔑 Los datos secretos NO están en el código: GitHub los pone al armar el APK
// (Settings → Secrets and variables → Actions).
val claveSecreta: String = (System.getenv("CLAVE_SECRETA") ?: "").trim()
val clavesFirma: String = (System.getenv("KEYSTORE_PASSWORD") ?: "").trim()

android {
    namespace = "ar.servicell.protector"
    compileSdk = 34

    defaultConfig {
        applicationId = "ar.servicell.protector"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
        buildConfigField("String", "CLAVE_SECRETA", "\"$claveSecreta\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("servicell") {
            storeFile = file("servicell.jks")
            storePassword = clavesFirma
            keyAlias = "servicell"
            keyPassword = clavesFirma
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("servicell")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
