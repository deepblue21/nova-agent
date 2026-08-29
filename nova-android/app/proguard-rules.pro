# NOVA / Project Horus — R8 kuralları (release).
#
# isMinifyEnabled=true ile birlikte devreye girer. Buradaki her kural bir
# gerekçeyle duruyor; "her ihtimale karşı" geniş -keep eklemeyin, aksi
# hâlde küçültmenin anlamı kalmaz.

# ——————————————————————————————————————————————————————————————
# LiteRT-LM (cihaz-üstü LLM motoru) — ZORUNLU
# ——————————————————————————————————————————————————————————————
# litertlm-android 0.13.1 AAR'ı consumer proguard kuralı İÇERMİYOR.
# Kütüphane JNI üzerinden Kotlin sınıflarına geri çağrı yapıyor
# (ör. Conversation$JniMessageCallbackImpl). R8 bunları yeniden
# adlandırırsa native taraf sınıfı bulamaz ve üretim sırasında
# UnsatisfiedLinkError / NoSuchMethodError alırsınız — üstelik yalnız
# release derlemesinde, yani ancak mağazada fark edersiniz.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** {
    native <methods>;
}

# JNI'den çağrılan her native metot imzası korunur.
-keepclasseswithmembernames class * {
    native <methods>;
}

# ——————————————————————————————————————————————————————————————
# OkHttp 5 / Okio
# ——————————————————————————————————————————————————————————————
# OkHttp kendi consumer kurallarını getiriyor; aşağıdakiler yalnız
# derleme zamanı opsiyonel sınıflar için gürültüyü susturur.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# ——————————————————————————————————————————————————————————————
# Kotlin / Coroutines
# ——————————————————————————————————————————————————————————————
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlinx.coroutines.**

# ——————————————————————————————————————————————————————————————
# Uygulama modelleri
# ——————————————————————————————————————————————————————————————
# Sohbet dışa aktarımı ve ayar göçü sınıf/alan adlarına bakmıyor
# (elle JSON kuruluyor), bu yüzden data sınıflarını korumaya gerek yok.
# Bu durum değişirse ilgili paketi buraya ekleyin.

# ——————————————————————————————————————————————————————————————
# Çökme izlerinin okunabilir kalması
# ——————————————————————————————————————————————————————————————
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
