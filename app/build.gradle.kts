plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.glass.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glass.player"
        minSdk = 31
        targetSdk = 35
        versionCode = 9
        versionName = "1.7.1"
        // اللغات اللي نحتاجها بس (مكتبات كومبوز وميديا تجيب عشرات اللغات)
        resourceConfigurations += listOf("ar", "en")
        // الجوالات الحديثة كلها arm64: نشيل مكتبات المعمارات الثانية
        ndk { abiFilters += "arm64-v8a" }
    }

    // مفتاح ثابت داخل المشروع: كل نسخة تنبني تتوقّع بنفس التوقيع، فالتحديث يركب فوق القديمة بدون ما تحذف التطبيق
    signingConfigs {
        create("glass") {
            storeFile = file("glassplayer.jks")
            storePassword = "glassplayer"
            keyAlias = "glass"
            keyPassword = "glassplayer"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("glass") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("glass")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/LICENSE*", "/META-INF/NOTICE*",
                "DebugProbesKt.bin", "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.guava:guava:33.3.1-android")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-common:$media3")
}
