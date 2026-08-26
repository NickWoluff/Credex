import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val credexVersionName = "1.1.0"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("Credex-app"))

android {
    namespace = "com.nickwoluff.credex"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nickwoluff.credex"
        minSdk = 29
        targetSdk = 37
        versionCode = 14
        versionName = credexVersionName
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint {
        // v26 is required because AAPT accepts adaptive-icon only in a versioned resource;
        // the matching v33 asset already supplies the themed monochrome layer.
        disable += setOf("ObsoleteSdkInt", "MonochromeLauncherIcon")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("Credex-v$credexVersionName-${variant.name}.apk")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.material.components)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(files("libs/reorderable-android-3.1.0.aar"))
    testImplementation(libs.junit)
    testImplementation(libs.json.test)
}
