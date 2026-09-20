plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val githubRepository = providers.gradleProperty("githubRepository").orElse("Nickyd36555/StreamFusionTV")

android {
    namespace = "com.stremiolivetv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.streamfusion.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
        buildConfigField("String", "GITHUB_REPOSITORY", "\"${githubRepository.get()}\"")
    }
    buildFeatures { buildConfig = true }
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        create("release") {
            storeFile = file(providers.environmentVariable("FUSION_KEYSTORE_FILE").orElse("streamfusion.keystore").get())
            storePassword = providers.environmentVariable("FUSION_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("FUSION_KEY_ALIAS").orElse("stream-fusion-tv").get()
            keyPassword = providers.environmentVariable("FUSION_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
