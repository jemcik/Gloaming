import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    jacoco
}

android {
    namespace = "com.jemcik.gloaming"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jemcik.gloaming"
        minSdk = 35
        targetSdk = 36
        // The RELEASE workflow passes these from the git tag; a local build
        // takes the fallbacks. They were hardcoded, and the consequence was not
        // cosmetic: every release shipped versionCode 1, and Android REFUSES to
        // install an APK whose versionCode is not higher than the installed
        // one. So 0.1 installed, and 0.2 would have failed with "app not
        // installed" for everyone who already had it - the release that
        // upgrades nobody.
        versionCode = (findProperty("gloamingVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("gloamingVersionName") as String?) ?: "0.1"
    }

    // Release signing comes from the ENVIRONMENT, never from the repo - see
    // .gitignore, which has refused signing material since before any existed.
    // CI decodes a base64 secret to a file and points GLOAMING_KEYSTORE at it.
    //
    // The keystore is what makes an APK upgradable, and getting versionCode
    // right was necessary but not sufficient. CI used to publish DEBUG APKs,
    // and the runner generates a throwaway debug key per run - so 0.1 and 0.2
    // went out signed by different certificates (789ee6a5... and 7fe1791e...)
    // and 0.2 could not install over 0.1. Measured on the phone rather than
    // assumed: INSTALL_FAILED_UPDATE_INCOMPATIBLE, "signatures do not match
    // newer version".
    val keystorePath: String? = System.getenv("GLOAMING_KEYSTORE")
    val signingReady = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

    signingConfigs {
        if (signingReady) create("release") {
            storeFile = file(keystorePath!!)
            storePassword = System.getenv("GLOAMING_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("GLOAMING_KEY_ALIAS") ?: "gloaming"
            keyPassword = System.getenv("GLOAMING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // Minification stays OFF. Turning it on is a real change to an app
            // that runs unattended overnight, and it belongs in its own pass
            // with its own testing rather than riding along with signing.
            isMinifyEnabled = false
            // With no keystore configured the APK is left UNSIGNED rather than
            // falling back to the debug key. A fallback would produce something
            // that looks releasable and cannot be upgraded - the exact bug this
            // block exists to fix. The release workflow refuses to publish an
            // APK that apksigner cannot verify, so this fails loudly in CI and
            // is merely inert on a laptop that has no key.
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // Robolectric needs the app's resources: Interruptions builds its sentences
    // out of them, and the point of testing it is the wording per locale.
    testOptions { unitTests.isIncludeAndroidResources = true }

    // Coverage of the unit tests. `./gradlew coverage` writes HTML and XML to
    // app/build/reports/jacoco/coverage.
    buildTypes { debug { enableUnitTestCoverage = true } }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.0")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// The unit tests run on 21. Only the TEST jvm: the app still compiles against
// the toolchain above.
//
// The original reason was that Robolectric's ASM could not read Java 25 class
// files ("Unsupported class file major version 69") while Gradle auto-
// provisioned a 25 JDK here. THAT REASON IS PROBABLY GONE: Robolectric 4.16.1
// declares ASM 9.8, and 9.8 is the release that added Java 25 support.
//
// It stays because nothing here can prove it. This machine now has only a 21
// JDK installed, so removing the pin "passes" for the wrong reason - the tests
// run on 21 either way - and both CI workflows pin java-version: '21'
// explicitly, so CI would never exercise the difference. Deleting it on that
// evidence would be deleting a guard because the thing it guards against
// cannot currently happen. Anyone with a 25 JDK can drop these five lines and
// find out.
val testJvm = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
tasks.withType<Test>().configureEach { javaLauncher.set(testJvm) }


// Robolectric loads classes through its own sandbox classloader, which the
// JaCoCo agent does not see unless told to instrument classes with no source
// location. Without this the report counted only the plain-JVM tests and read
// 1%, which looks like an answer and is an artefact.
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("coverage") {
    dependsOn("testDebugUnitTest")
    reports { html.required = true; xml.required = true }
    // Compose generates a great deal of synthetic code that no test can reach
    // and no one would want to; measuring it would only lower a number without
    // telling anyone anything.
    val filter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*_Factory.*", "**/ComposableSingletons*.*", "**/*\$\$serializer.*",
        "**/*Kt$*.class"
    )
    // AGP 9 puts them here, not in tmp/kotlin-classes - which still held stale
    // output from the app's former package name and made the whole report read
    // 0%, plausibly, because every class it measured had indeed never run.
    classDirectories.setFrom(
        fileTree(
            layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
        ) { exclude(filter) }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("**/testDebugUnitTest.exec") }
    )
}
