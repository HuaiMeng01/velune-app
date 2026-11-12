plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.veluneagent.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Robolectric 需要真实的资源/Manifest 才能解析 Context、SharedPreferences 等
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.okhttp)
    // SSH：Android 没有系统 ssh 客户端，只能走纯 Java 实现（mwiede 是 jcraft 的维护分支）
    implementation(libs.jsch)

    testImplementation(libs.junit)
    // 单测里替代 Android 的 org.json stub，否则 JSONObject 在 JVM 上不可用
    testImplementation("org.json:json:20240303")
    // 注：QqBindControllerTest 用「真实时间但很短（20ms 间隔）」跑，没有引入
    // kotlinx-coroutines-test —— 缓存里只有 1.9.0，而 coroutines-core-jvm 1.10.2 的
    // BOM 会把它强制升到 1.10.2（离线没有该版本），强行 force 版本又会撞二进制不兼容
    // Robolectric：QqResponder 依赖 Context（SharedPreferences / PromptStore），
    // 没有它就只能起真机或 MockWebServer 才能覆盖 handle() 这条主链路
    testImplementation("org.robolectric:robolectric:4.14.1")
    // 注意：不要引入 androidx.test:core —— 它的 AAR 在本模块（无 appcompat/material 资源）
    // 合并时会因为 style/WhiteBackgroundTheme 缺失导致 processDebugUnitTestResources 失败。
    // Robolectric 里取 Context 用 RuntimeEnvironment.getApplication() 即可。
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
