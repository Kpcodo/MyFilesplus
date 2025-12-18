# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\kayal\AppData\Local\Android\Sdk\tools\proguard\proguard-android-optimize.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
# Optimization
-allowaccessmodification
-repackageclasses ''
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Keep generic classes causing issues if stripped
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep R class members
-keepclassmembers class **.R$* {
    public static <fields>;
}
