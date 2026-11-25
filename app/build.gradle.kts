import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    jacoco
}

val archivoFirma = rootProject.file("keystore.properties")
val propiedadesFirma =
    Properties().apply {
        if (archivoFirma.exists()) archivoFirma.inputStream().use { load(it) }
    }

android {
    namespace = "cl.duoc.therionlabs"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "cl.duoc.therionlabs"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "2.3"

        manifestPlaceholders["traficoHttp"] = "false"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (archivoFirma.exists()) {
        signingConfigs {
            create("entrega") {
                storeFile = rootProject.file(propiedadesFirma.getProperty("storeFile"))
                storePassword = propiedadesFirma.getProperty("storePassword")
                keyAlias = propiedadesFirma.getProperty("keyAlias")
                keyPassword = propiedadesFirma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["traficoHttp"] = "true"
        }
        release {
            if (archivoFirma.exists()) signingConfig = signingConfigs.getByName("entrega")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:3.14.9")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    implementation("androidx.navigation:navigation-compose:2.9.6")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val clasesLogica =
    fileTree(
        layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
    ) {
        include("cl/duoc/therionlabs/domain/**")
        include("cl/duoc/therionlabs/util/**")
        include("cl/duoc/therionlabs/data/repository/**")
        include("cl/duoc/therionlabs/data/viewmodel/**")
        include("cl/duoc/therionlabs/network/**")
    }

val coberturaDebug by
    tasks.registering(JacocoReport::class) {
        dependsOn("testDebugUnitTest")
        executionData(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
        classDirectories.setFrom(clasesLogica)
        sourceDirectories.setFrom(files("src/main/java"))
        reports {
            html.required = true
            xml.required = true
            csv.required = true
        }
    }

val verificarCobertura by
    tasks.registering(JacocoCoverageVerification::class) {
        dependsOn(coberturaDebug)
        executionData(coberturaDebug.map { it.executionData })
        classDirectories.setFrom(clasesLogica)
        sourceDirectories.setFrom(files("src/main/java"))
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }
