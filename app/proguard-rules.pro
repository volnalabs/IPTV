# Media3, Coil и ZXing поставляют свои правила; дополнительно ничего не нужно.
-dontwarn org.xmlpull.v1.**

# Мост между приложением и страницей плагинов: имена методов вызываются из JavaScript
-keepclassmembers class com.volnalabs.tv.data.Plugins$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
