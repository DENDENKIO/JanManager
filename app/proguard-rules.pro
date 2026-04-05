# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt / Dagger
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class com.google.dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembernames class kotlinx.serialization.json.** { *; }
-keep class com.example.janmanager.ui.navigation.Route** { *; }
-keepclassmembers class com.example.janmanager.ui.navigation.Route** { *; }

# AI Response Data
-keep class com.example.janmanager.util.AiResponseData { *; }
-keepclassmembers class com.example.janmanager.util.AiResponseData { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
