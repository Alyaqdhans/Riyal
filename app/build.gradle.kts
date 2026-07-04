plugins {
    alias(libs.plugins.android.application)
    // AGP 9 built-in Kotlin compiles the Kotlin sources (no kotlin-android plugin),
    // but the Compose compiler plugin is still applied per-module, version-matched
    // to AGP's embedded Kotlin.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.alyaqdhan.riyal"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.alyaqdhan.riyal"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // Material Components (Views) 1.14.0: app theme + native MaterialFadeThrough/SharedAxis
    // fragment transitions for the XML nav-graph routing.
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Compose Material 3 1.5.0-alpha23: Material 3 Expressive screens rendered inside
    // the routed fragments (MaterialExpressiveTheme, expressive MotionScheme, LoadingIndicator…).
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    testImplementation(libs.junit)
}
