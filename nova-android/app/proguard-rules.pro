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
# WorkManager — indirme işinin SINIF ADI · ZORUNLU
# ——————————————————————————————————————————————————————————————
# WorkManager, iş kuyruğa girerken worker'ın TAM SINIF ADINI kendi
# veritabanına yazar ve çalıştıracağı anda `Class.forName` ile çözer.
# R8'in ürettiği ad derlemeler arasında SABİT DEĞİL.
#
# Sonuç: sürüm N'de kuyruğa girmiş yarım bir indirme, sürüm N+1'de artık
# var olmayan bir ada bakar. Varsayılan WorkerFactory istisnayı yutup işi
# "başarısız" işaretler — yani kullanıcının 8,6 GB'a kadar çıkabilen yarım
# indirmesi, uygulama güncellenince sessizce ölür. Özel bir WorkerFactory
# kullanılsaydı doğrudan ClassNotFoundException ile çökerdi.
#
# Debug'da minify kapalı olduğu için bu HİÇ görünmez; yalnız release'de,
# üstelik yalnız GÜNCELLEMEDEN SONRA ortaya çıkar.
#
# Yalnız bu sınıf korunuyor: adı ve WorkManager'ın yansımayla çağırdığı
# yapıcı. Kütüphaneyi topluca açmıyoruz.
-keep class com.nova.agent.llm.local.ModelDownloadWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

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
