# TAG Takip ProGuard / R8 kuralları

# Kotlinx Serialization (type-safe navigation route'ları)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tagtakip.pro.**$$serializer { *; }
-keepclassmembers class com.tagtakip.pro.** { *** Companion; }
-keepclasseswithmembers class com.tagtakip.pro.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel

# Google Play Services
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# Tink (security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
