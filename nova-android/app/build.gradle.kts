import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ——————————————————————————————————————————————————————————————
// Yükleme (upload) imzası — Play bloker B2.
//
// Anahtarın kendisi ve parolaları BU DEPODA YOK ve olmayacak:
// `nova-android/keystore.properties` gitignore'lu, `.jks` de öyle.
// Dosya yoksa release derlemesi imzasız çıkar (CI ve katkıcılar için
// derleme kırılmaz); dosya varsa `bundleRelease` doğrudan imzalanır.
//
// Üretmek için: `scripts/new-upload-keystore.ps1`
// ——————————————————————————————————————————————————————————————
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasUploadKeystore = keystorePropsFile.exists() &&
    !keystoreProps.getProperty("storeFile").isNullOrBlank()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nova.agent"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.nova.agent"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Faz 9 (Basit/Gelişmiş mod + backend/örnekleme) ve 10A (mDNS keşfi +
        // eşleme). Cihazda eski yapının üzerine kurulduğunda hangi sürümün
        // çalıştığı Ayarlar > Uygulama bilgisi'nden görülebilsin.
        versionCode = 4
        versionName = "1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasUploadKeystore) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Anahtar yoksa imzasız kal — sessizce debug anahtarıyla imzalamak,
            // Play'e yanlış anahtarla yüklemekten çok daha kötü bir hata olurdu.
            signingConfig = if (hasUploadKeystore) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // Release ile aynı R8 yolundan geçmeyen hızlı döngü.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // 16 KB sayfa uyumu: .so dosyaları sıkıştırılmadan, hizalı paketlenir.
            // LiteRT kütüphanelerinin tamamı zaten 16384 hizalı (2026-08-09 doğrulandı).
            useLegacyPackaging = false
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.version",
            )
        }
    }

    // AAB, cihaz ABI'sine göre bölünür: arm64 telefona x86 LiteRT
    // kütüphaneleri (~26 MB) indirilmez.
    bundle {
        abi { enableSplit = true }
        language { enableSplit = false } // arayüz tek dil; bölmek kazanç sağlamaz
        density { enableSplit = true }
    }

    lint {
        // Play'e giden yolda lint hatası derlemeyi durdurur.
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }
}

// kotlinOptions {} DSL'i Kotlin 2.2'de kullanımdan kaldırıldı, 2.3'te siliniyor.
// Doğru yer artık bu üst düzey blok.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    // K2: indirme WorkManager isine tasindi; surec olse de surer.
    implementation(libs.androidx.work.runtime)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.coroutines.android)

    // Faz 1 — cihaz-üstü LLM (LiteRT-LM Kotlin API, Google Maven). Sabit sürüm.
    implementation(libs.litertlm.android)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
