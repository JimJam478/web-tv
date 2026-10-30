package app.webtv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONObject;

/**
 * Opens HOME full screen, signed in across launches, videos able to go full screen. Ads: ad/tracker hosts blocked,
 * pop-up windows and non-web links dropped, leftover ad boxes hidden. Holding Back goes home.
 */
public class MainActivity extends Activity {
    private static final String HOME = Config.URL;
    /** CSS pixels across the screen. The TV's own 2x density gives pages 960, a tablet layout; 1920 is desktop. */
    private static final int PAGE_WIDTH = Config.PAGE_WIDTH;
    private static final long UPDATE_EVERY_MS = 7L * 24 * 60 * 60 * 1000;

    private FrameLayout root;
    private WebView web;
    private View fullscreenVideo;
    private WebChromeClient.CustomViewCallback exitFullscreen;
    private Cursor cursor;
    private volatile Blocklist blocklist;   // null until loaded, or if the asset failed to load
    /**
     * Sets every viewport tag to PAGE_WIDTH (adding one if missing), then keeps doing so: Chrome obeys the last tag,
     * and some sites add their own width=device-width after loading. Media queries then pick the desktop layout.
     */
    private static final String FIT_WIDTH = "(function(){function fit(){"
            + "var ms=document.querySelectorAll('meta[name=viewport]');"
            + "if(!ms.length){var m=document.createElement('meta');m.name='viewport';"
            + "(document.head||document.documentElement).appendChild(m);ms=[m]}"
            + "for(var i=0;i<ms.length;i++)if(ms[i].content!='width=" + PAGE_WIDTH + "')"
            + "ms[i].content='width=" + PAGE_WIDTH + "'}"
            + "fit();if(!window.webtvFit){window.webtvFit=1;new MutationObserver(fit).observe(document.documentElement,"
            + "{childList:true,subtree:true,attributes:true,attributeFilter:['content']})}})()";
    private volatile String hideAds;   // script adding EasyList's hide rules to a page; null until loaded
    private Thread blocklistLoader;
    private boolean clearHistoryOnLoad;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // ponytail: screen stays on the whole time the app is open, so the TV's screensaver never cuts into a
        // video; if a forgotten open app matters, keep it on only while a video plays.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // ~275k hosts take a moment to read: off the UI thread, requests wait for it before the first one goes out.
        blocklistLoader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long t = SystemClock.uptimeMillis();
                    // The weekly download once there is one, else the copy bundled in the APK.
                    File hosts = new File(getFilesDir(), "hosts.txt"), css = new File(getFilesDir(), "hide.css");
                    boolean downloaded = hosts.exists() && css.exists();
                    blocklist = new Blocklist(downloaded ? new FileInputStream(hosts) : getAssets().open("hosts.txt"));
                    hideAds = "if(!document.getElementById('webtv-hide')){var s=document.createElement('style');"
                            + "s.id='webtv-hide';s.textContent=" + JSONObject.quote(read(
                                    downloaded ? new FileInputStream(css) : getAssets().open("hide.css")))
                            + ";(document.head||document.documentElement).appendChild(s)}";
                    Log.i("WebTV", "ad lists loaded (" + (downloaded ? "downloaded" : "bundled") + "): "
                            + blocklist.size() + " hosts in " + (SystemClock.uptimeMillis() - t) + " ms");
                    if (!downloaded || System.currentTimeMillis() - hosts.lastModified() > UPDATE_EVERY_MS) {
                        updateLists();
                    }
                    web.post(new Runnable() {   // the first page may have finished before the lists did
                        @Override
                        public void run() {
                            web.evaluateJavascript(hideAds, null);
                        }
                    });
                } catch (IOException e) {
                    Log.e("WebTV", "ad lists failed to load, ads not blocked", e);
                }
            }
        });

        web = new WebView(this);
        blocklistLoader.start();   // after web exists: it posts to it when done
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // Desktop Chrome on Linux, same version: sites send desktop pages, and Google sign-in doesn't refuse "wv".
        s.setUserAgentString(s.getUserAgentString().replaceFirst("\\([^)]*Android[^)]*\\)", "(X11; Linux x86_64)")
                .replace(" Mobile", "").replace("; wv", "").replaceFirst(" Version/[0-9.]+", ""));
        Log.d("WebTV", "user agent " + s.getUserAgentString());
        s.setUseWideViewPort(true);   // honor the page's viewport tag, which FIT_WIDTH sets to PAGE_WIDTH
        s.setLoadWithOverviewMode(true);   // then zoom out so that width fills the screen
        s.setSupportMultipleWindows(true);   // with onCreateWindow refusing, window.open and target=_blank do nothing
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {   // every link stays in the app, login redirects included
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return blockAds(req);
            }

            /** A page leaving for an ad host or a non-web link (intent://, market://) stays put instead. */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri url = req.getUrl();
                String scheme = url.getScheme();
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    Log.d("WebTV", "blocked scheme " + scheme);
                    return true;
                }
                // Lists not loaded yet: let it through, shouldInterceptRequest still empties it once they are.
                if (blocklist != null && blocklist.blocks(url.getHost())) {
                    Log.d("WebTV", "blocked nav " + url.getHost());
                    return true;
                }
                return false;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                view.evaluateJavascript(FIT_WIDTH, null);
                if (hideAds != null) view.evaluateJavascript(hideAds, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(FIT_WIDTH, null);   // again: the page's own scripts may have reset it
                if (hideAds != null) view.evaluateJavascript(hideAds, null);   // no-op if commit already added it
                if (clearHistoryOnLoad) {
                    clearHistoryOnLoad = false;
                    view.clearHistory();
                }
            }
        });
        // A site's service worker fetches for its pages, and those requests skip the WebViewClient above.
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest req) {
                return blockAds(req);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message resultMsg) {
                Log.d("WebTV", "blocked popup");
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                fullscreenVideo = view;
                exitFullscreen = callback;
                root.addView(view, root.indexOfChild(cursor), new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenVideo == null) return;
                root.removeView(fullscreenVideo);
                fullscreenVideo = null;
                web.setVisibility(View.VISIBLE);
                exitFullscreen.onCustomViewHidden();
            }
        });

        root = new FrameLayout(this);
        root.addView(web);
        cursor = new Cursor(this);
        root.addView(cursor);   // added last: drawn over the page and over a full-screen video
        setContentView(root);

        if (state != null) web.restoreState(state);
        else web.loadUrl(HOME);
        web.requestFocus();
    }

    /** The remote's arrows move the cursor and OK clicks under it; every other key (Back, media keys) is unchanged. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int dx = 0, dy = 0;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: dx = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dx = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: dy = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dy = 1; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) tap();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int step = Math.min(6 + e.getRepeatCount() * 3, 40) * cursor.getWidth() / 1280;   // holding a key speeds up
        float x = cursor.x + dx * step, y = cursor.y + dy * step;
        float maxX = cursor.getWidth() - 1, maxY = cursor.getHeight() - 1;
        // Pushing past the top or bottom scrolls the page instead (not a full-screen video, which has nothing to scroll).
        if (fullscreenVideo == null && (y < 0 || y > maxY)) web.scrollBy(0, dy * step * 3);
        cursor.moveTo(Math.max(0, Math.min(x, maxX)), Math.max(0, Math.min(y, maxY)));
        return true;
    }

    /** A finger tap at the cursor: the page (or the video's own controls) can't tell it from a touch. */
    private void tap() {
        cursor.moveTo(cursor.x, cursor.y);   // shows it again if it had hidden
        long t = SystemClock.uptimeMillis();
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent m = MotionEvent.obtain(t, SystemClock.uptimeMillis(), action, cursor.x, cursor.y, 0);
            m.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            root.dispatchTouchEvent(m);
            m.recycle();
        }
    }

    /**
     * Empty response for an ad/tracker host, else null (load it). Runs on a WebView IO thread, never the UI thread,
     * so it can wait for the lists.
     */
    private WebResourceResponse blockAds(WebResourceRequest req) {
        try {
            blocklistLoader.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String host = req.getUrl().getHost();
        if (blocklist == null || !blocklist.blocks(host)) return null;
        Log.d("WebTV", "blocked " + host);
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    /** Fetches fresh ad lists in the background; they take effect next launch, so the open page never shifts. */
    private void updateLists() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i("WebTV", "lists updated: " + ListUpdater.update(getFilesDir()));
                } catch (IOException e) {
                    Log.w("WebTV", "lists update failed, keeping the old ones; retrying next launch", e);
                }
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Holding Back loads HOME with no history behind it: the way out of a page that keeps Back from leaving. */
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent e) {
        if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyLongPress(keyCode, e);
        if (fullscreenVideo != null) web.getWebChromeClient().onHideCustomView();
        clearHistoryOnLoad = true;
        web.loadUrl(HOME);
        return true;   // the release then counts as canceled, so it doesn't also go back
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = i.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreenVideo != null) web.getWebChromeClient().onHideCustomView();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();   // the login cookie is on disk before the TV can kill the app
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    /** An arrow pointer drawn over everything; hides after a few idle seconds so it never sits on a video. */
    private static final class Cursor extends View {
        private static final long HIDE_AFTER_MS = 4000;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private final Runnable hide = new Runnable() {
            @Override
            public void run() {
                setVisibility(INVISIBLE);
            }
        };
        float x = -1, y;

        Cursor(Context c) {
            super(c);
            fill.setColor(0xFFFFFFFF);
            edge.setColor(0xFF000000);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(3);
            float u = c.getResources().getDisplayMetrics().density * 1.5f;
            arrow.moveTo(0, 0);
            arrow.lineTo(0, 17 * u);
            arrow.lineTo(4.5f * u, 13 * u);
            arrow.lineTo(7.5f * u, 20 * u);
            arrow.lineTo(10 * u, 19 * u);
            arrow.lineTo(7 * u, 12 * u);
            arrow.lineTo(12.5f * u, 12 * u);
            arrow.close();
        }

        void moveTo(float nx, float ny) {
            x = nx;
            y = ny;
            setVisibility(VISIBLE);
            removeCallbacks(hide);
            postDelayed(hide, HIDE_AFTER_MS);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (x < 0) moveTo(w / 2f, h / 2f);   // starts in the middle of the screen
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(x, y);
            canvas.drawPath(arrow, fill);
            canvas.drawPath(arrow, edge);
            canvas.restore();
        }
    }
}
