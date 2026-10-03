# Moshi rules
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class *
-keep class * extends com.squareup.moshi.JsonAdapter
-keep class com.example.ai.** { *; }

# OkHttp/Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-dontwarn okio.**
-dontwarn javax.annotation.**
