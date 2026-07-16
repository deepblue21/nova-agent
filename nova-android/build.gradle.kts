plugins {
    id("com.android.application") version "8.5.2" apply false
    // 2.2.21: litertlm-android 0.13.1, Kotlin 2.3 metadata'sıyla derlendi;
    // Kotlin 2.2.x bunu okuyabilir ve kütüphanenin stdlib pini de 2.2.21.
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
