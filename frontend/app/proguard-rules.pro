# kotlinx.serialization keeps the generated serializer next to the class it belongs to.
# R8 cannot see the reflective link, so both sides have to survive shrinking.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class org.javerlabd.homecenter.tv.api.model.** {
    *** Companion;
}
-keepclasseswithmembers class org.javerlabd.homecenter.tv.api.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit reads generic return types of interface methods through reflection.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
