plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.reshqmess"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.reshqmess"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // ... other stuff ...

    // 1. LiveData for Compose (Fixes 'observeAsState')
    implementation("androidx.compose.runtime:runtime-livedata:1.5.0")

    // 2. Permissions (Fixes 'ExperimentalPermissionsApi')
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // 3. Google Nearby (Fixes 'google' reference if related to Mesh)
    implementation("com.google.android.gms:play-services-nearby:19.0.0")

    // 4. OSMDroid (Fixes MapView if you pulled the map code)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    // --- CORE ENGINE TOOLS ---
    implementation("com.google.android.gms:play-services-nearby:19.0.0")  // Wi-Fi Direct
    implementation("com.google.code.gson:gson:2.10.1")                    // Fixes 'gson' error
    implementation("androidx.compose.runtime:runtime-livedata:1.5.0")     // LiveData

    // --- MAP TOOLS ---
    implementation("org.osmdroid:osmdroid-android:6.1.18")                // OSM Map

    // --- COMPATIBILITY TOOLS (For your teammate's XML code) ---
    implementation("androidx.appcompat:appcompat:1.6.1")                  // Fixes 'AppCompatActivity'
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")    // Fixes 'ConstraintLayout'
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    // ... keep your other dependencies like core-ktx, lifecycle, etc ...

    // 1. FIX FOR "INTELLIGENT WIFI SWITCHING" (Must be version 19.0.0 or higher)
    implementation("com.google.android.gms:play-services-nearby:19.2.0")

    // 2. FIX FOR "PRO ICONS" (Radar, EmergencyShare, Security)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // 3. MAPS & LOCATION
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Standard Compose stuff (Keep what you already have here)
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.gms:play-services-nearby:19.2.0")

    // 2. FIX FOR "PRO ICONS" (Radar, EmergencyShare, Security)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // 3. MAPS & LOCATION
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Standard Compose stuff (Keep what you already have here)
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    dependencies {
        // ... other stuff ...

        // THIS IS THE KEY TO INTELLIGENT SWITCHING (Must be 19.2.0 or newer)
        implementation("com.google.android.gms:play-services-nearby:19.2.0")

        // THIS IS FOR THE ICONS
        implementation("androidx.compose.material:material-icons-extended:1.6.0")

        // ... other stuff ...
        val camerax_version = "1.3.0"
        implementation("androidx.camera:camera-core:${camerax_version}")
        implementation("androidx.camera:camera-camera2:${camerax_version}")
        implementation("androidx.camera:camera-lifecycle:${camerax_version}")
        implementation("androidx.camera:camera-view:${camerax_version}")
        implementation("com.google.mlkit:text-recognition:16.0.0")
    }
}