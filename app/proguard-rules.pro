# kotlinx.serialization: keep generated serializers of the app's @Serializable DTOs.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.criptopanel.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.criptopanel.**$$serializer { *; }
-keepclasseswithmembers class app.criptopanel.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit: interfaces are only reached through reflection; suspend functions need Continuation's signature.
-keep,allowobfuscation interface app.criptopanel.**.*Api
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
