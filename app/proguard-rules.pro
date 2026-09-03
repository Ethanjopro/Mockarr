# Mockarr release rules. Room, Hilt, MapLibre, Retrofit and OkHttp ship their own
# consumer rules; what follows covers the seams they cannot see.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# Readable crash traces in Play Console (the mapping file is uploaded with the bundle).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization — every @Serializable DTO (OSRM, Photon, Open-Meteo,
# saved-route JSON) needs its generated serializer and Companion kept.
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.mockarr.**$$serializer { *; }

# Retrofit service interfaces (private nested interfaces in core:routing) —
# generic signatures must survive for suspend functions to resolve.
-keep,allowobfuscation,allowshrinking interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Optional TLS providers OkHttp probes for at runtime; absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
