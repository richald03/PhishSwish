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
val GEMINI_API_KEY_1: String = (project.findProperty("GEMINI_API_KEY_1") ?: localProperties.getProperty("GEMINI_API_KEY_1") ?: "").toString()
val GEMINI_API_KEY_2: String = (project.findProperty("GEMINI_API_KEY_2") ?: localProperties.getProperty("GEMINI_API_KEY_2") ?: "").toString()
val GEMINI_API_KEY_3: String = (project.findProperty("GEMINI_API_KEY_3") ?: localProperties.getProperty("GEMINI_API_KEY_3") ?: "").toString()
val GEMINI_API_KEY_4: String = (project.findProperty("GEMINI_API_KEY_4") ?: localProperties.getProperty("GEMINI_API_KEY_4") ?: "").toString()
val GEMINI_API_KEY_5: String = (project.findProperty("GEMINI_API_KEY_5") ?: localProperties.getProperty("GEMINI_API_KEY_5") ?: "").toString()
val GEMINI_API_KEY_6: String = (project.findProperty("GEMINI_API_KEY_6") ?: localProperties.getProperty("GEMINI_API_KEY_6") ?: "").toString()
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
        buildConfigField("String", "GEMINI_API_KEY_1", "\"$GEMINI_API_KEY_1\"")
        buildConfigField("String", "GEMINI_API_KEY_2", "\"$GEMINI_API_KEY_2\"")
        buildConfigField("String", "GEMINI_API_KEY_3", "\"$GEMINI_API_KEY_3\"")
        buildConfigField("String", "GEMINI_API_KEY_4", "\"$GEMINI_API_KEY_4\"")
        buildConfigField("String", "GEMINI_API_KEY_5", "\"$GEMINI_API_KEY_5\"")
        buildConfigField("String", "GEMINI_API_KEY_6", "\"$GEMINI_API_KEY_6\"")
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
    
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)
    
    implementation(libs.okhttp)

    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // ML Kit OCR
    implementation(libs.mlkit.text.recognition)
    
    // Guava for ListenableFuture
    implementation(libs.guava)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
