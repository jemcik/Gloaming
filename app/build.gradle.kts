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

    buildTypes {
        release { isMinifyEnabled = false }
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
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Robolectric instruments bytecode, and its ASM cannot read Java 25 class files
// ("Unsupported class file major version 69"). Gradle auto-provisions a 25 JDK
// on this machine, so the unit tests are pinned to 21, which it understands.
// Only the TEST jvm: the app still compiles against the toolchain above.
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
