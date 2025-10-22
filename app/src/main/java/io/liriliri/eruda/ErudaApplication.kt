package io.liriliri.eruda

import android.app.Application
import android.webkit.WebView

class ErudaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Enable WebView debugging in debug builds
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
