import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localSigningProps = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasLocalSigning = localSigningProps.containsKey("storeFile")

android {
    namespace = "com.colourswift.avarionxlite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.colourswift.avarionxlite"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        resourceConfigurations += listOf("en", "ar", "de", "es", "ja", "pl", "pt")
        externalNativeBuild {
            cmake {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    signingConfigs {
        create("release") {
            if (hasLocalSigning) {
                keyAlias = localSigningProps["keyAlias"] as String
                keyPassword = localSigningProps["keyPassword"] as String
                storeFile = file(localSigningProps["storeFile"] as String)
                storePassword = localSigningProps["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasLocalSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
