package com.atatis.bosque;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final String GAME_URL = "https://a-tatis-bosque-cartas.emp33.chatgpt.site";
    private WebView gameView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        gameView = new WebView(this);
        gameView.setBackgroundColor(Color.BLACK);
        gameView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(gameView);

        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " AtatisBosqueAPK/1.1");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(gameView, true);

        gameView.setWebChromeClient(new WebChromeClient());
        gameView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    view.loadUrl(uri.toString());
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.postDelayed(() -> centerInitialMenu(view), 250);
                view.postDelayed(() -> centerInitialMenu(view), 1200);
            }
        });

        if (savedInstanceState == null) {
            gameView.loadUrl(GAME_URL);
        } else {
            gameView.restoreState(savedInstanceState);
        }
    }

    private void centerInitialMenu(WebView view) {
        String script =
                "(function(){" +
                "function txt(el){return ((el&&el.innerText)||'').replace(/\\s+/g,' ').trim();}" +
                "function findButton(label){var nodes=document.querySelectorAll('button,a,[role=\"button\"]');for(var i=0;i<nodes.length;i++){if(txt(nodes[i]).toLowerCase()===label)return nodes[i];}return null;}" +
                "function apply(){" +
                "var ex=findButton('explorar'),op=findButton('opciones');if(!ex||!op)return false;" +
                "var node=ex,menu=null;" +
                "while(node&&node!==document.body){var t=txt(node).toLowerCase();if(node.contains(op)&&t.indexOf('explorar')>=0&&t.indexOf('opciones')>=0){menu=node;if(t.indexOf('a-tatis')>=0||t.indexOf('aventura')>=0||t.indexOf('mi álbum')>=0||t.indexOf('mi album')>=0)break;}node=node.parentElement;}" +
                "if(!menu)return false;" +
                "var s=menu.style;" +
                "s.setProperty('position','fixed','important');" +
                "s.setProperty('left','50%','important');" +
                "s.setProperty('top','50%','important');" +
                "s.setProperty('transform','translate(-50%,-50%)','important');" +
                "s.setProperty('margin','0','important');" +
                "s.setProperty('width','min(92vw,760px)','important');" +
                "s.setProperty('max-width','760px','important');" +
                "s.setProperty('max-height','92vh','important');" +
                "s.setProperty('overflow','auto','important');" +
                "s.setProperty('display','flex','important');" +
                "s.setProperty('flex-direction','column','important');" +
                "s.setProperty('align-items','center','important');" +
                "s.setProperty('justify-content','center','important');" +
                "s.setProperty('text-align','center','important');" +
                "s.setProperty('z-index','2147483000','important');" +
                "Array.prototype.forEach.call(menu.children,function(c){if(c&&c.style){c.style.setProperty('margin-left','auto','important');c.style.setProperty('margin-right','auto','important');}});" +
                "return true;}" +
                "if(!apply()){var observer=new MutationObserver(function(){if(apply())observer.disconnect();});observer.observe(document.documentElement,{childList:true,subtree:true});setTimeout(function(){observer.disconnect();},12000);}" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        gameView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (gameView != null && gameView.canGoBack()) {
            gameView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) {
            gameView.stopLoading();
            gameView.destroy();
        }
        super.onDestroy();
    }
}
