# RaiChess ProGuard/R8 rules

# Keep @JavascriptInterface methods so the Stockfish WebView UCI bridge
# survives minification in release builds. Without this, R8 strips/renames
# Bridge.onReady/onMessage/onError (only ever called reflectively from JS)
# and Stockfish would silently fall back to RaiEngine in release APKs.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.raichess.data.engine.StockfishWasmEngine$Bridge { *; }
