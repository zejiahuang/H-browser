-keepattributes JavascriptInterface,*Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.h.browser.userscript.GmBridge { *; }
-keep class com.h.browser.ui.NativeBridge { *; }

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
