plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Resolve o versionName no momento do build, em vez de depender de alguém
 * lembrar de editar esse arquivo toda vez que uma release é publicada.
 */
fun resolverVersionName(): String {
    System.getenv("GITHUB_REF_NAME")
        ?.takeIf { it.startsWith("v") && it.getOrNull(1)?.isDigit() == true }
        ?.let { return it.removePrefix("v") }

    System.getenv("APP_VERSION_NAME")
        ?.takeIf { it.isNotBlank() }
        ?.let { return it.removePrefix("v") }

    // Utiliza a API nativa de Providers do Gradle, compatível com o Configuration Cache
    return try {
        val saidaGit = providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()

        if (saidaGit.startsWith("v")) {
            saidaGit.removePrefix("v")
        } else {
            "1.0.19-dev"
        }
    } catch (_: Exception) {
        "1.0.19-dev"
    }
}

/** maior*10000 + menor*100 + correção (1.5 -> 10500, 1.0.83 -> 10083). Nunca pode diminuir. */
fun resolverVersionCode(versionName: String): Int {
    val numeros = versionName.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
    if (numeros.isEmpty()) return 1
    return numeros[0] * 10000 + numeros.getOrElse(1) { 0 } * 100 + numeros.getOrElse(2) { 0 }
}

android {
    namespace = "com.example.financacelular"
    compileSdk = 37
    // NDK e CMake do llama.cpp (MotorLlm); o CI instala as mesmas versões
    ndkVersion = "27.1.12297006"

    // --- CONFIGURAÇÃO DA ASSINATURA OFICIAL ---
    signingConfigs {
        create("release") {
            // Lê as variáveis de ambiente que o GitHub Actions vai injetar
            val storeFileEnv = System.getenv("SIGNING_STORE_FILE")
            if (!storeFileEnv.isNullOrBlank()) {
                storeFile = file(storeFileEnv)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.financacelular"
        minSdk = 26
        targetSdk = 36
        versionName = resolverVersionName()
        versionCode = resolverVersionCode(versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ONNX Runtime só no arm64 (celulares atuais); em outras ABIs o modelo não abre e a IA desliga sozinha
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // llama.cpp estático dentro de libmotor_llm.so (ver src/main/cpp/CMakeLists.txt)
        externalNativeBuild {
            cmake {
                arguments += listOf("-DGGML_OPENMP=OFF", "-DLLAMA_CURL=OFF", "-DBUILD_SHARED_LIBS=OFF")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            // --- ATRIBUI A ASSINATURA À RELEASE ---
            signingConfig = signingConfigs.getByName("release")

            // R8: encolhe e otimiza o código; regras para o Drive e o login em proguard-rules.pro
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // .so comprimido dentro do APK: o ONNX Runtime cai de ~33 MB para ~12 MB no download (extraído na instalação)
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // `-Dia.exportar=true` chega aos testes da JVM (ia/avaliar_atipicos.py grava as pontuações do Kotlin)
        unitTests.all { test ->
            providers.systemProperty("ia.exportar").orNull?.let { test.systemProperty("ia.exportar", it) }
            // `-Dexportar.fatos=<saida.jsonl>` liga o ExportarFatosParaTreino (dados de treino do LLM)
            providers.systemProperty("exportar.fatos").orNull?.let { test.systemProperty("exportar.fatos", it) }
        }
    }
    // Os schemas exportados do Room (schemas/) chegam ao teste de migração como assets do build debug
    // (o Robolectric lê os assets do app; a release não leva os schemas)
    sourceSets {
        getByName("debug").assets.directories.add("$projectDir/schemas")
        // Golden da IA (tokens e vetores do Python) também visível nos testes no aparelho
        getByName("androidTest").resources.directories.add("$projectDir/src/test/resources")
    }
}

configurations.named("testImplementation") {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Google Sign-In e Play Services
    implementation("com.google.android.gms:play-services-auth:21.5.0")

    // Google API Client para acesso ao Drive AppData
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    // Credential Manager do Android (para Google Sign-In moderno)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.biometric:biometric:1.1.0")

    // IA offline: embeddings de descrições (modelo em assets/ia)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.0")

    testImplementation(libs.junit)
    // Nos testes JVM o org.json do android.jar é só um stub, e o ONNX Runtime precisa da versão desktop
    testImplementation("org.json:json:20250517")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.31.0")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(libs.androidx.junit)
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}