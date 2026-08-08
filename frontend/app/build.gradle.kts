import org.openapitools.generator.gradle.plugin.extensions.OpenApiGeneratorGenerateExtension

plugins {
    // AGP 9 compiles Kotlin itself; org.jetbrains.kotlin.android must not be applied
    // alongside it. The Compose and serialization compiler plugins are still separate.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.openapi.generator)
}

/**
 * The client API is generated from the committed OpenAPI snapshot, never hand-written.
 * The specification is the only contract between the Java server and this Kotlin client;
 * refresh the snapshot with `frontend/openapi/refresh.ps1` after changing the REST API.
 */
val generatedApiDir: Provider<Directory> = layout.buildDirectory.dir("generated/openapi")

/** Relative to this module, which is how the new source-set DSL wants directories. */
val generatedApiSources = "build/generated/openapi/src/main/kotlin"

// Configured through the extension object rather than an `openApiGenerate { }` block: in
// this build `library` resolves to something else inside that block, and the assignment
// fails to compile.
val openApi = extensions.getByType(OpenApiGeneratorGenerateExtension::class.java)
openApi.generatorName.set("kotlin")
openApi.library.set("jvm-retrofit2")
// Forward slashes on purpose: the generator parses this as a URI, and a Windows path with
// backslashes fails validation before it reads the file.
openApi.inputSpec.set(
    rootProject.layout.projectDirectory.file("openapi/homecenter-openapi.json")
        .asFile.invariantSeparatorsPath
)
openApi.outputDir.set(generatedApiDir.map { it.asFile.path })
openApi.packageName.set("org.javerlabd.homecenter.tv.api")
openApi.apiPackage.set("org.javerlabd.homecenter.tv.api")
openApi.modelPackage.set("org.javerlabd.homecenter.tv.api.model")
openApi.generateApiTests.set(false)
openApi.generateModelTests.set(false)
openApi.generateApiDocumentation.set(false)
openApi.generateModelDocumentation.set(false)
openApi.configOptions.set(
    mapOf(
        "serializationLibrary" to "kotlinx_serialization",
        // Instants stay strings. kotlinx.serialization has no built-in serializer for
        // java.time types, and the client only ever formats them for display.
        "dateLibrary" to "string",
        "useCoroutines" to "true",
        // Keeps the server's VIDEO / PHOTO / AUDIO spelling instead of camelCasing it.
        "enumPropertyNaming" to "original",
        "sourceFolder" to "src/main/kotlin",
        "omitGradleWrapper" to "true",
    )
)

android {
    namespace = "org.javerlabd.homecenter.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.javerlabd.homecenter.tv"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets {
        named("main") {
            kotlin.directories += generatedApiSources
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time on API 23; the generated API hands out ISO-8601 strings to parse.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates request logging; BuildConfig.VERSION_NAME is shown in settings.
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

// Sources must exist before anything compiles them, KSP included. With built-in Kotlin the
// jvmTarget follows android.compileOptions.targetCompatibility, so it needs no separate
// setting here.
tasks.matching { it.name.startsWith("compile") || it.name.startsWith("ksp") }.configureEach {
    dependsOn(tasks.openApiGenerate)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.viewmodel.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.retrofit.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
