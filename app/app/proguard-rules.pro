# ===============================================================
# Reglas ProGuard / R8 para Control de Stock Textil FP
# ===============================================================

# 1. Preservar POJOs y modelos de datos de Firestore contra ofuscación
-keepclassmembers class com.ifpleonardo.controlstocktextil.data.model.** {
    <fields>;
    <methods>;
}
-keep class com.ifpleonardo.controlstocktextil.data.model.** { *; }

# 2. FastExcel (Lector y escritor de hojas de cálculo)
-keep class org.dhatim.fastexcel.** { *; }
-dontwarn org.dhatim.fastexcel.**

# 3. ZXing (Generador de códigos QR)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# 4. Glide (Carga y optimización de imágenes)
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# 5. Firebase Firestore y Auth
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-dontwarn com.google.firebase.**