plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.bm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.bm.app"
        minSdk = 32
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }
    }
}

dependencies {
    // 核心库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM + 核心组件
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    
    // Compose Icons 依赖
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle 相关
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation(libs.androidx.lifecycle.service)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    // 添加 lifecycle-process 依赖
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    // 确保添加了 lifecycle-runtime-compose 依赖
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("androidx.lifecycle:lifecycle-runtime-android:2.10.0")
    // 新增：SavedState 依赖（FloatWindowService 必须）
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.savedstate.ktx)

    // 测试/调试依赖
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    // 网络请求 Retrofit + OkHttp
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
}