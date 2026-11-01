# ML Kit finds its text recognition classes by reflection, and native code calls back into them
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.libraries.barhopper.** { *; }
-dontwarn com.google.mlkit.**
