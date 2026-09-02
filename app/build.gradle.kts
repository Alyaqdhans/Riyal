import java.util.Properties

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
        versionCode = 3
        versionName = "1.51"
    }

    // The key that signs a published build. Its location and passwords come from
    // local.properties, which is gitignored, so the keystore itself never enters the
    // repository. Absent that, there is no release signing config at all and the
    // release build comes out unsigned - deliberately, because an APK signed with any
    // other key cannot update the one already installed on someone's phone.
    signingConfigs {
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        val store = props.getProperty("riyal.storeFile")?.let { rootProject.file(it) }
        if (store != null && store.exists()) {
            create("release") {
                storeFile = store
                storePassword = props.getProperty("riyal.storePassword")
                keyAlias = props.getProperty("riyal.keyAlias")
                keyPassword = props.getProperty("riyal.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
    // Vico: proper chart library (Analysis 6-month columns), Compose + Material 3.
    implementation(libs.vico.compose.m3)
    testImplementation(libs.junit)
}
