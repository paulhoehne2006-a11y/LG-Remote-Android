plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
val robolectricJarDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricJars = tasks.register<Sync>("robolectricJars") {
    from(robolectricRuntime)
    into(robolectricJarDir)
}

android {
    namespace = "com.modernlghub.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.modernlghub.remote"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "2.4.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sideload build: signed with the debug key so it installs without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric renders the Compose screens on the JVM (see ui/ScreenshotTest).
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // `./gradlew testDebugUnitTest -Pscreenshots` writes PNGs to docs/screenshots;
            // without the flag the same tests only compose and lay out every screen.
            test.systemProperty("roborazzi.test.record", project.hasProperty("screenshots").toString())
            test.systemProperty("screenshots.dir", rootProject.file("docs/screenshots").absolutePath)
            test.inputs.property("screenshots", project.hasProperty("screenshots"))
            test.maxHeapSize = "2g"
            // Robolectric's own downloader does not get through every proxy; Gradle fetches the
            // Android framework jar instead and Robolectric runs offline against it.
            test.dependsOn(robolectricJars)
            test.systemProperty("robolectric.offline", "true")
            test.systemProperty("robolectric.dependency.dir", robolectricJarDir.get().asFile.absolutePath)
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    robolectricRuntime(libs.robolectric.android.all)
}
