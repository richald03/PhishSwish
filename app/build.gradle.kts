import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

// STEP 2 - Define API keys (preferring project properties as requested, with local.properties fallback)
val GEMINI_API_KEY: String = (project.findProperty("GEMINI_API_KEY") ?: localProperties.getProperty("GEMINI_API_KEY") ?: "").toString()
val vtApiKey: String = (project.findProperty("VT_API_KEY") ?: localProperties.getProperty("VT_API_KEY") ?: "").toString()

android {
    namespace = "com.example.phishswish"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.phishswish"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject the API keys into BuildConfig
        buildConfigField("String", "VT_API_KEY", "\"$vtApiKey\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$GEMINI_API_KEY\"")
    }

    buildFeatures {
        buildConfig = true
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    
    implementation(libs.okhttp)

    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
