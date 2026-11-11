import java.security.MessageDigest

plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
val sourceDigest = MessageDigest.getInstance("SHA-256").apply {
    rootProject.fileTree(rootProject.projectDir) {
        include("**/src/**/*.kt", "**/src/**/*.xml", "**/src/**/*.aidl", "**/src/**/*.ttf", "**/api/**/*.aidl", "**/*.gradle.kts", "data/*.json", "gradle.properties", "gradle/wrapper/gradle-wrapper.properties")
        exclude("**/build/**", ".gradle/**")
    }.files.sortedBy { it.relativeTo(rootProject.projectDir).path }.forEach { file ->
        update(file.relativeTo(rootProject.projectDir).path.toByteArray())
        update(file.readBytes())
    }
}.digest().joinToString("") { "%02x".format(it) }
abstract class PrepareEvaluationAssets : DefaultTask() {
    @get:InputFiles abstract val inputsToCopy: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun generate() {
        val output = outputDirectory.get().asFile.apply { mkdirs() }
        inputsToCopy.files.forEach { it.copyTo(output.resolve(it.name), overwrite = true) }
    }
}
val evaluationAssets = tasks.register<PrepareEvaluationAssets>("evaluationAssets") {
    inputsToCopy.from(rootProject.file("data/models.json"), rootProject.file("data/definitions.json"), rootProject.file("data/cases.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/evaluationAssets"))
}
android {
    namespace = "io.github.lucf15.localai.runner"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.lucf15.localai.runner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SOURCE_FINGERPRINT", "\"$sourceDigest\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Keep the opt-in, real-weight probe out of routine connected-test discovery.
        testInstrumentationRunnerArguments["class"] = "io.github.lucf15.localai.runner.RunnerSmokeTest,io.github.lucf15.localai.runner.ManagerNavigationTest"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(evaluationAssets, PrepareEvaluationAssets::outputDirectory)
}
dependencies {
    implementation(project(":libs:runtime"))
    implementation(project(":libs:sdk"))
    implementation("androidx.navigation3:navigation3-runtime:1.2.1")
    implementation("androidx.navigation3:navigation3-ui:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")
    implementation(project(":libs:ui"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Compose test defaults to an older Espresso; 3.7 uses the public input API on Android 17.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
