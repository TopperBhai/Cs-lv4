import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import com.android.build.gradle.tasks.MergeSourceSetFolders
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20"
}

android {
    namespace  = "com.tungsten.fcl"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // =========================================================
    // Keys & API Setup
    // =========================================================
    var localProperty: Properties? = null
    if (file("${rootDir}/local.properties").exists()) {
        localProperty = Properties()
        file("${rootDir}/local.properties")
            .inputStream()
            .use { localProperty!!.load(it) }
    }

    val pwd         = System.getenv("FCL_KEYSTORE_PASSWORD")
                   ?: localProperty?.getProperty("pwd")
    val curseApiKey = System.getenv("CURSE_API_KEY")
                   ?: localProperty?.getProperty("curse.api.key")
    val oauthApiKey = System.getenv("OAUTH_API_KEY")
                   ?: localProperty?.getProperty("oauth.api.key")

    if (localProperty != null
        && localProperty!!.getProperty("arch", "all") == "arm64") {
        System.setProperty("arch", "arm64")
    }

    // =========================================================
    // Signing Configs
    // =========================================================
    signingConfigs {
        create("FCLKey") {
            storeFile     = file("../key-store.jks")
            storePassword = pwd
            keyAlias      = "FCL-Key"
            keyPassword   = pwd
        }
        create("FCLDebugKey") {
            storeFile     = file("../debug-key.jks")
            storePassword = "FCL-Debug"
            keyAlias      = "FCL-Debug"
            keyPassword   = "FCL-Debug"
        }
    }

    // =========================================================
    // Default Config
    // =========================================================
    defaultConfig {
        applicationId = "com.tungsten.fcl"
        minSdk        = libs.versions.minSdk.get().toInt()
        targetSdk     = libs.versions.targetSdk.get().toInt()
        versionCode   = 1302
        versionName   = "1.3.0.2"
        // ✅ Pre-emptive Fix for Firebase 64k Method Limit
        multiDexEnabled = true 
    }

    // =========================================================
    // Build Types
    // =========================================================
    buildTypes {

        // ===== RELEASE =====
        getByName("release") {
            isMinifyEnabled   = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig   = signingConfigs.getByName("FCLKey")
            isDebuggable    = false
            isJniDebuggable = false
        }

        // ===== FORDEBUG =====
        create("fordebug") {
            initWith(getByName("debug"))
            signingConfig       = signingConfigs.getByName("FCLDebugKey")
            isMinifyEnabled     = false
            isShrinkResources   = false
            isDebuggable        = true
        }

        // ===== ALL VARIANTS =====
        configureEach {
            resValue("string", "app_version",
                defaultConfig.versionName.toString())
            resValue("string", "curse_api_key",
                curseApiKey.toString())
            resValue("string", "oauth_api_key",
                oauthApiKey.toString())
        }
    }

    // =========================================================
    // Android Components - JRE Asset Filter
    // =========================================================
    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                if (output is com.android.build.api.variant.impl.VariantOutputImpl) {

                    val abiId = output.getFilter(ABI)?.identifier ?: "all"

                    output.outputFileName =
                        "CSLauncher-${variant.buildType}" +
                        "-${defaultConfig.versionName}" +
                        "-${abiId}.apk"

                    val variantName = variant.name
                        .replaceFirstChar { it.uppercaseChar() }

                    afterEvaluate {
                        val task = tasks
                            .named("merge${variantName}Assets")
                            .get() as MergeSourceSetFolders

                        task.doLast {
                            val arch      = System.getProperty("arch", "all")
                            val assetsDir = task.outputDir.get().asFile
                            val jreList   = listOf(
                                "jre8", "jre17", "jre21", "jre25"
                            )

                            println("CS Launcher Build | arch=$arch")

                            jreList.forEach { jre ->
                                val runtimeDir =
                                    "$assetsDir/app_runtime/java/$jre"

                                File(runtimeDir).listFiles()?.forEach { f ->
                                    val keep = arch == "all"
                                        || f.name == "version"
                                        || f.name.contains("universal")
                                        || f.name == "bin-${arch}.tar.xz"

                                    if (!keep) {
                                        println(
                                            "Removing: ${f.name} " +
                                            "→ deleted=${f.delete()}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================
    // Compile Options
    // =========================================================
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // =========================================================
    // Packaging
    // =========================================================
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts        += listOf("**/libbytehook.so")
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/*.txt",
                "/*.md"
            )
        }
    }

    // =========================================================
    // Build Features
    // =========================================================
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // =========================================================
    // ABI Splits
    // =========================================================
    splits {
        val arch = System.getProperty("arch", "all")
        if (arch != "all") {
            abi {
                isEnable = true
                reset()
                when (arch) {
                    "arm"    -> include("armeabi-v7a")
                    "arm64"  -> include("arm64-v8a")
                    "x86"    -> include("x86")
                    "x86_64" -> include("x86_64")
                }
            }
        }
    }

    // =========================================================
    // Kotlin Options
    // =========================================================
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // =========================================================
    // Lint
    // =========================================================
    lint {
        abortOnError       = false
        checkReleaseBuilds = false
    }
}

// =========================================================
// ✅ Dependencies (Restored to Perfect Modern State)
// =========================================================
dependencies {

    implementation(fileTree(mapOf(
        "dir"     to "libs",
        "include" to listOf("*.jar", "*.aar")
    )))

    // ===== Project Modules =====
    implementation(project(":FCLCore"))
    implementation(project(":FCLLibrary"))
    implementation(project(":FCLauncher"))
    implementation(project(":Terracotta"))

    // ===== Existing Libraries =====
    implementation(libs.taptargetview)
    implementation(libs.nanohttpd)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.opennbt)
    implementation(libs.gson)
    implementation(libs.appcompat)
    implementation(libs.core.splashscreen)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.glide)
    implementation(libs.touchcontroller)
    implementation(libs.palette.ktx)
    implementation(libs.gamepad.remapper)
    implementation(libs.segmented.button)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
}

// =========================================================
// Update Map Task
// =========================================================
tasks.register("updateMap") {
    doLast {
        val mapFile = file("${rootDir}/version_map.json")
        val updated = mapFile.readLines().map { line ->
            when {
                line.contains("versionCode") ->
                    line.replace(
                        Regex("[0-9]+"),
                        android.defaultConfig.versionCode.toString()
                    )
                line.contains("versionName") ->
                    line.replace(
                        Regex("\\d+(\\.\\d+)+"),
                        android.defaultConfig.versionName.toString()
                    )
                line.contains("date") ->
                    line.replace(
                        Regex("\\d{4}\\.\\d{2}\\.\\d{2}"),
                        SimpleDateFormat("yyyy.MM.dd").format(Date())
                    )
                line.contains("url") ->
                    line.replace(
                        Regex("\\d+(\\.\\d+)+"),
                        android.defaultConfig.versionName.toString()
                    )
                else -> line
            }
        }
        mapFile.writeText(updated.joinToString("\n"), Charsets.UTF_8)
    }
}
