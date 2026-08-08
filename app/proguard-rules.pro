# kotlinx.serialization: the generated serializers are looked up reflectively
# from the companion object, so R8 must keep them for every @Serializable type.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class app.immichshare.data.**$$serializer { *; }
-keepclassmembers class app.immichshare.data.** {
    *** Companion;
}
-keepclasseswithmembers class app.immichshare.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit: interface methods are read via reflection, and generic return
# types must survive so the converter can resolve them.
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface app.immichshare.data.ImmichApi
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp ships references to optional platform APIs that are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
