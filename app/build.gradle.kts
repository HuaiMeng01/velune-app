import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * release 签名。
 *
 * 密钥不进仓库：读根目录的 keystore.properties（已在 .gitignore 里），
 * 文件不存在时回落 debug 签名 —— 保证 `assembleRelease` 在任何机器上都能跑通
 * （产物不可发布，但可以验证 R8 与资源收缩没把代码裁坏）。
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { stream -> load(stream) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "com.veluneagent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.veluneagent.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.0.0.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 之前是 false：发出去的是 debuggable 包，无混淆、无死代码消除，
            // 而且根本没有可用的 release 通道。这里补上 R8 与资源收缩。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    packaging {
        resources {
            // jsch 与 jspecify 各带一份 OSGi 清单，Android 不需要 OSGi 元数据，
            // 不排除会在 mergeJavaResource 阶段报 duplicate path
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/versions/9/OSGI-INF/*"
        }
        jniLibs {
            // ⚠️ 必须 true（= manifest 的 extractNativeLibs="true"）：
            // AGP 默认 false 时，so 以未压缩形式留在 APK 内、安装时不落盘，
            // nativeLibraryDir 里没有实体文件 —— Android 10+ 的 W^X 只允许 exec
            // 「包管理器解压到 nativeLibraryDir 的文件」，于是 libproot.so 永远
            // 无法被执行，Linux 环境显示「缺少 proot 运行时」。
            // 代价是安装后多占 ~250KB（so 落盘一份），这是让 proot 可执行的前提。
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AGP 8 起默认关闭；版本号在设置页/关于页要用 BuildConfig.VERSION_NAME 统一取值
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:messages"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * UI 约束检查原本只 `tasks.register`，没有挂到任何生命周期上，
 * 所以 `./gradlew check` / `assembleRelease` 都不会执行它 —— 等于没有。
 * 挂在 app 的 check 上：app 是聚合入口，一次即可覆盖全仓源码。
 */
tasks.named("check") {
    dependsOn(rootProject.tasks.named("checkUiConstraints"))
}
