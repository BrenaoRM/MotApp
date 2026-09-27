plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Resolve o versionName no momento do build, em vez de depender de alguém
 * lembrar de editar esse arquivo toda vez que uma release é publicada.
 * Ordem de prioridade:
 *   1) GITHUB_REF_NAME — preenchida automaticamente pelo GitHub Actions
 *      quando o workflow é disparado por push de uma tag (ex.: "v1.0.21").
 *   2) APP_VERSION_NAME — variável que o workflow pode definir manualmente,
 *      útil se a tag for criada por uma etapa do próprio workflow em vez de
 *      disparar o build diretamente. Ajuste o workflow pra exportar essa
 *      variável com o número certo antes da etapa de build, se for o caso.
 *   3) `git describe --tags` — cobre builds locais feitas a partir de um
 *      checkout com tags (ex.: testando localmente uma tag já publicada).
 *   4) Valor fixo de fallback — só usado em builds de desenvolvimento sem
 *      CI e sem tags no histórico local; marcado com "-dev" pra nunca ser
 *      confundido com uma release de verdade.
 */
fun resolverVersionName(): String {
    System.getenv("GITHUB_REF_NAME")
        ?.takeIf { it.startsWith("v") && it.getOrNull(1)?.isDigit() == true }
        ?.let { return it.removePrefix("v") }

    System.getenv("APP_VERSION_NAME")
        ?.takeIf { it.isNotBlank() }
        ?.let { return it.removePrefix("v") }

    try {
        val processo = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .redirectErrorStream(true)
            .start()
        val saida = processo.inputStream.bufferedReader().readText().trim()
        processo.waitFor()
        if (processo.exitValue() == 0 && saida.startsWith("v")) {
            return saida.removePrefix("v")
        }
    } catch (_: Exception) {
        // git indisponível nesse ambiente de build — ignora e cai no padrão abaixo
    }

    return "1.0.19-dev"
}

android {
    namespace = "com.example.financacelular"
    compileSdk = 37

    // --- NOVO: CONFIGURAÇÃO DA ASSINATURA OFICIAL ---
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
        targetSdk = 34
        versionCode = 1
        // Antes era um texto fixo ("1.0.19") que nunca acompanhava as releases
        // publicadas pelo GitHub Actions — por isso o app sempre reportava a
        // mesma versão antiga pro checador de atualização, não importa qual
        // .apk tivesse sido realmente instalado. Agora ela é resolvida a
        // partir da tag do release/git no momento do build (função abaixo).
        versionName = resolverVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // --- ATRIBUI A ASSINATURA À RELEASE ---
            signingConfig = signingConfigs.getByName("release")

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
        buildConfig = true
    }
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
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}