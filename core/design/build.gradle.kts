plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.veluneagent.core.design"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.haze)
    // MIUIX（HyperOS 设计语言）：用 api 暴露给各 feature 模块，
    // 否则每个模块都要重复声明，且容易版本漂移
    api(libs.miuix.ui)
    api(libs.miuix.icons)
    // 删掉了 miuix-squircle：项目里的 SquircleShape / SquircleCapsule
    // 是 core/design 自绘的（shape/Squircle.kt），从没用过 miuix 的实现。
    implementation(libs.compose.material3)
    // 二维码编码（扫码绑定用）。放 core:design 而不是 feature:settings：
    // 任何页面都可能要画二维码，编码器属于"画图"这件事，不属于某个业务页
    implementation(libs.zxing.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
