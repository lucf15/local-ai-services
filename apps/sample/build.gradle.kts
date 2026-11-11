plugins { kotlin("plugin.serialization"); id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
// Local debug identity, separate from the manager. Never used for release signing.
val sampleKey = layout.buildDirectory.file("signing/sample-debug.jks")
val generateSampleDebugKey = tasks.register<Exec>("generateSampleDebugKey") {
    outputs.file(sampleKey)
    onlyIf { !sampleKey.get().asFile.exists() }
    doFirst { sampleKey.get().asFile.parentFile.mkdirs() }
    commandLine("${System.getProperty("java.home")}/bin/keytool", "-genkeypair", "-keystore", sampleKey.get().asFile.absolutePath,
        "-storepass", "android", "-keypass", "android", "-alias", "sample", "-keyalg", "RSA", "-validity", "3650",
        "-dname", "CN=Local AI Sample debug", "-noprompt")
}
android {
    namespace = "io.github.lucf15.localai.reader"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.lucf15.localai.reader"
        minSdk = 26; targetSdk = 36
        versionCode = 1; versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["class"] = "io.github.lucf15.localai.reader.ReaderBoundaryTest,io.github.lucf15.localai.reader.SampleNavigationTest"
    }
    signingConfigs {
        create("sample") {
            storeFile = sampleKey.get().asFile; storePassword = "android"
            keyAlias = "sample"; keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sample") }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
tasks.matching { it.name in setOf("validateSigningDebug", "validateSigningDebugAndroidTest", "packageDebug", "packageDebugAndroidTest") }
    .configureEach { dependsOn(generateSampleDebugKey) }
dependencies {
    implementation("androidx.navigation3:navigation3-runtime:1.2.1")
    implementation("androidx.navigation3:navigation3-ui:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")
    implementation(project(":libs:sdk"))
    implementation(project(":libs:ui"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
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
