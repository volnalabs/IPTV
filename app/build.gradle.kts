plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.volnalabs.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.volnalabs.tv"
        minSdk = 21
        targetSdk = 35
        // На GitHub номер сборки растёт сам — новая версия ставится поверх старой
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(1) ?: 1
        versionName = System.getenv("VOLNA_VERSION")?.removePrefix("v") ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Ключ лежит в репозитории, чтобы любая сборка (локальная или GitHub Actions)
            // ставилась поверх предыдущей как обновление. Можно заменить своим через
            // переменные окружения VOLNA_KEYSTORE / VOLNA_KEYSTORE_PASSWORD / VOLNA_KEY_ALIAS / VOLNA_KEY_PASSWORD.
            storeFile = file(System.getenv("VOLNA_KEYSTORE") ?: "volna-tv.jks")
            storePassword = System.getenv("VOLNA_KEYSTORE_PASSWORD") ?: "volnatv"
            keyAlias = System.getenv("VOLNA_KEY_ALIAS") ?: "volnatv"
            keyPassword = System.getenv("VOLNA_KEY_PASSWORD") ?: "volnatv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.logging", "stdout")
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
