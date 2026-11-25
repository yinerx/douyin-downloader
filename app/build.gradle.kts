import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? {
    val fromEnv = System.getenv(env)?.takeIf { it.isNotBlank() }
    if (fromEnv != null) return fromEnv
    return keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.yiner.videodownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yiner.videodownloader"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.2.4"
        val updateUrl = (project.findProperty("updateManifestUrl") as String?)
            ?: "https://github.com/yinerx/douyin-downloader/releases/latest/download/latest.json"
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    signingConfigs {
        create("stable") {
            // The release key is a PKCS12 store. Never leave this unset on the release build type.
            storeType = signingValue("storeType", "KEYSTORE_TYPE") ?: "pkcs12"
            val path = signingValue("storeFile", "KEYSTORE_FILE")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.configureEach {
    if (name == "validateSigningRelease") {
        doFirst {
            val stable = android.signingConfigs.getByName("stable")
            val store = stable.storeFile
            val missing = store == null || !store.exists() ||
                stable.storePassword.isNullOrBlank() ||
                stable.keyAlias.isNullOrBlank() ||
                stable.keyPassword.isNullOrBlank()
            if (missing) {
                throw GradleException(
                    "Release builds require the PKCS12 signing key (KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). They do not fall back to the debug key.",
                )
            }
        }
    }
}

dependencies {
    implementation(project(":parser"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
