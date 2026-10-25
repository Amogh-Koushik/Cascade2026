import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pratibimb.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pratibimb.app"
        // Both demo phones must be Android 13+; that is also when POST_NOTIFICATIONS appeared.
        minSdk = 33
        // Kept at 34 on purpose: an app targeting 35 that calls setInterruptionFilter on
        // Android 15 gets its own separate DND mode instead of changing the global filter,
        // which is a different behaviour from the Mode A we are testing.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The engine contract lives once, in contracts/, and is compiled straight into the app.
    sourceSets {
        getByName("main") {
            java.srcDir("../../contracts/kotlin")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
