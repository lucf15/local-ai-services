plugins { id("com.android.library") }
android {
    namespace = "io.github.lucf15.localai.sdk"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

// Use the same SDK compiler as AGP, including its API compatibility checker.
// No fork of AndroidX's unpublished plugin or hard-coded local SDK path is needed.
val aidlTool = androidComponents.sdkComponents.aidl
val aidlSources = fileTree("src/main/aidl") { include("**/*.aidl") }
val apiDump = layout.buildDirectory.dir("aidl-api/current")
val dumpBinderApi = tasks.register<Exec>("dumpBinderApi") {
    inputs.files(aidlSources)
    inputs.file(aidlTool.flatMap { it.executable })
    outputs.dir(apiDump)
    doFirst {
        delete(apiDump)
        commandLine(listOf(aidlTool.get().executable.get().asFile.absolutePath,
            "--dumpapi", "--out=${apiDump.get().asFile.absolutePath}", "-I${file("src/main/aidl")}") +
            aidlSources.files.sortedBy { it.path }.map { it.absolutePath })
    }
}
val checkBinderApi = tasks.register<Exec>("checkBinderApi") {
    dependsOn(dumpBinderApi)
    inputs.dir("api/v1")
    inputs.dir(apiDump)
    doFirst {
        commandLine(aidlTool.get().executable.get().asFile.absolutePath, "--checkapi=compatible",
            file("api/v1").absolutePath, apiDump.get().asFile.absolutePath)
    }
}
tasks.matching { it.name == "preBuild" || it.name == "check" }.configureEach { dependsOn(checkBinderApi) }
