import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.orynnx.codexquota"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.orynnx.codexquota"
        minSdk = 29
        targetSdk = 37
        versionCode = 9
        versionName = "0.9.0"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint {
        // v26 is required because AAPT accepts adaptive-icon only in a versioned resource;
        // the matching v33 asset already supplies the themed monochrome layer.
        disable += setOf("ObsoleteSdkInt", "MonochromeLauncherIcon")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.json.test)
}
