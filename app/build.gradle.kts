import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lsucu.pods"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lsucu.pods"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.2"
    }

    // ключ лежит вне проекта (D:/Android/keys), пароли — в keystore.properties (не в git)
    val keyProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        create("release") {
            if (keyProps.isNotEmpty()) {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keyProps.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    // Фото AirPods (рендеры Apple) лежат отдельно и в git не попадают.
    // Публичная сборка: ./gradlew assembleRelease -PpublicBuild — без фото, с рисованными картинками.
    if (!project.hasProperty("publicBuild")) {
        sourceSets["main"].res.srcDir("src/photos/res")
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // доступ к скрытому API L2CAP-сокетов (без root)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
