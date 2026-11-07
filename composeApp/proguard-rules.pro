# Proguard rules for PhimBud Mobile (KMP + Android)
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations

-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.phimbud.mobile.**$$serializer { *; }
-keepclassmembers class com.phimbud.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.phimbud.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Core Models safe from aggressive field stripping
-keep class com.phimbud.mobile.core.model.** { *; }

# Ktor & Coroutines & Coil3
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn coil3.**
-dontwarn slf4j.**
-dontwarn org.slf4j.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 / ExoPlayer (consumer rules bundled in Media3 AARs handle reflection entry points)
-dontwarn androidx.media3.**
