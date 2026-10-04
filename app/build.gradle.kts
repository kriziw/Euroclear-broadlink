plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is optional: supply all four values as environment variables or Gradle
// properties (e.g. in ~/.gradle/gradle.properties). Without them the release APK is unsigned.
fun signingValue(name: String): String? =
    (providers.environmentVariable(name).orNull ?: providers.gradleProperty(name).orNull)
        ?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("BL3372_KEYSTORE_PATH")
val releaseKeystorePassword = signingValue("BL3372_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("BL3372_KEY_ALIAS")
val releaseKeyPassword = signingValue("BL3372_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null }

// version.txt is the single source of the version; release-please bumps it in each release PR.
val appVersionName: String = providers.fileContents(rootProject.layout.projectDirectory.file("version.txt"))
    .asText.get().trim()

/** 1.2.3 -> 1002003, so every release has a higher versionCode than the one before. */
val appVersionCode: Int = appVersionName.split('.').map(String::toInt)
    .let { (major, minor, patch) -> major * 1_000_000 + minor * 1_000 + patch }

android {
    namespace = "io.github.kriziw.bl3372setup"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.kriziw.bl3372setup"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
