package ca.cuni.heples;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ListActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.util.ArraySet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.webkit.ClientCertRequest;
import android.webkit.GeolocationPermissions;
import android.webkit.MimeTypeMap;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.OverScroller;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.Scroller;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Timer;

public class BrowserActivity extends Activity {

    protected ViewGroup m_frame;
    protected ProgressBar m_progressbar;
    protected Handler m_handler;
    protected int m_visibility ;

    // browser options
    protected boolean m_fullscreen = false;
    protected boolean m_loadimg = true;
    protected boolean m_jsen = true;
    protected int m_textsize = 18 ;
    protected int m_textzoom = 112 ;

    protected final int MSG_FULLSCREEN = 1001;
    protected final int MSG_OPENLINK = 1002;
    protected final int MSG_OPENPAGESMENU = 1003;

    protected final static String pagesfile = "pages" ;
    protected final static String bookmarkfile = "bookmarks" ;
    protected final static String defaultUrl = "http://www.google.com/";

    protected final int ic_pages[] = {
            R.drawable.ic_pages,
            R.drawable.ic_pages_1,
            R.drawable.ic_pages_2,
            R.drawable.ic_pages_3,
            R.drawable.ic_pages_4,
            R.drawable.ic_pages_5,
            R.drawable.ic_pages_6,
            R.drawable.ic_pages_7,
            R.drawable.ic_pages_8,
            R.drawable.ic_pages_9,
            R.drawable.ic_pages_9p
    };

    class WebEntry {
        int     id  ;
        private Bundle state;
        private String url;
        private String title;
        private Bitmap icon;

        public WebEntry() {
            id = View.generateViewId();
        }

        public WebEntry(Bundle st) {
            this();
            state = st;
        }

        void save(WebView v) {
            url = v.getUrl();
            title = v.getTitle();
            icon = v.getFavicon();
            state = new Bundle();
            v.saveState(state);
        }

        void restore(WebView v) {
            if (state != null) {
                v.restoreState(state);
            }
            else if(url!=null) {
                v.loadUrl(url);
            }
            if( currentWeb()==v) {
                if (title != null) {
                    setTitle(title);
                }
                if (icon != null) {
                    setIcon(icon);
                }
            }
        }

        Bundle getState() {
            return state;
        }

        String getTitle() {
            return title;
        }

        String getUrl() {
            return url;
        }

        Bitmap getIcon() {
            return icon;
        }
    };

    protected ArrayList<WebEntry> m_savedWeb = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setContentView(R.layout.activity_browser);
        loadOptions();

        getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);

        setContentView(R.layout.activity_browser);
        m_frame = (ViewGroup) findViewById(R.id.main_frame);
        m_progressbar = (ProgressBar) findViewById(R.id.progressBar) ;
        m_progressbar.setMax(10000);

        m_handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == MSG_FULLSCREEN) {
                    if( !m_handler.hasMessages(MSG_FULLSCREEN) )
                        fullscreen(m_fullscreen);
                } else if (msg.what == MSG_OPENLINK) {
                    Bundle data = msg.getData();
                    if (data != null) {
                        String url = data.getString("url");
                        if (url != null) {
                            if (msg.arg1 == 2001) {
                                // open in new tab
                                newWeb(url);
                            } else if (msg.arg1 == 2002) {
                                // open external
                                openExternal( url ) ;
                            } else if (msg.arg1 == 2003) {
                                // copy link
                                ClipboardManager clipboard = (ClipboardManager)
                                        getSystemService(Context.CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(ClipData.newPlainText("link", url));
                            }
                        }
                    }
                } else if (msg.what == MSG_OPENPAGESMENU) {
                }
            }
        };

        registerForContextMenu(m_frame);
        m_frame.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        if ( m_frame.getChildAt( m_frame.getChildCount()-1 ) instanceof  WebView ) {
                            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                                getActionBar().show();
                                if (m_frame.getSystemUiVisibility() != 0) {
                                    m_frame.setSystemUiVisibility(0);
                                }
                                if (m_fullscreen) {
                                    fullscreen_delay(5000);
                                }
                            } else {
                                getActionBar().hide();
                            }
                        }
                        else {
                            m_frame.setSystemUiVisibility(m_visibility);
                         }
                    }
                });

        if (savedInstanceState != null) {
            m_savedWeb.clear();
            int tabcount = savedInstanceState.getInt("WebViewCount", 0);
            Bundle state;
            int i;
            for (i = 0; i < tabcount; i++) {
                state = savedInstanceState.getBundle("WebView" + i);
                if (state != null) {
                    WebEntry sw = new WebEntry(state);
                    m_savedWeb.add(sw);
                }
            }
        } else {
            Intent intent = getIntent();
            if (intent != null) {
                String url = intent.getDataString();
                if (url != null)
                    newWeb(url);
            }
        }

        bookmarkRestore();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = intent.getDataString();
        if (url != null)
            newWeb(url);
    }

    // progress from 0 to 100
    private void setProgress() {
        WebView view = currentWeb();
        if( view!=null && view.getVisibility() == View.VISIBLE ) {
            int progress = view.getProgress() * 100;
            int xprogress = m_progressbar.getProgress();
            if (xprogress > progress) {
                xprogress = 0;
            }
            if (progress < 10000 && m_progressbar.getAlpha() < 0.1f ) {
                m_progressbar.animate().setDuration(500).alpha(0.5f);
            }

            ObjectAnimator ani = ObjectAnimator.ofInt(m_progressbar, "progress", xprogress, progress);
            ani.setDuration( (progress - xprogress) / 10).setAutoCancel(true);
            if (progress >= 10000) {
                ani.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (m_progressbar.getProgress() >= 10000 && m_progressbar.getAlpha()>0.4f)
                            m_progressbar.animate().setDuration(500).alpha(0.0f);
                    }

                });
            }
            ani.start();
        }
    }

    private void loadOptions() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        m_fullscreen = prefs.getBoolean("fullscreen", false);
        m_loadimg = prefs.getBoolean("loadimg", true);
        m_jsen = prefs.getBoolean("javascript", true);
        m_textzoom = prefs.getInt("textzoom", 112) ;
        m_textsize = prefs.getInt("textsize", 18) ;
    }

    private void saveOptions() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        boolean x_fullscreen = prefs.getBoolean("fullscreen", false);
        boolean x_loadimg = prefs.getBoolean("loadimg", true);
        boolean x_jsen = prefs.getBoolean("javascript", true);
        int x_textzoom = prefs.getInt("textzoom", 112) ;
        int x_textsize = prefs.getInt("textsize", 18) ;

        if( x_fullscreen != m_fullscreen ||
                x_loadimg != m_loadimg ||
                x_jsen != m_jsen ||
                x_textzoom != m_textzoom ||
                x_textsize != m_textsize
                ) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("fullscreen", m_fullscreen);
            editor.putBoolean("loadimg", m_loadimg);
            editor.putBoolean("javascript", m_jsen);
            editor.putInt("textzoom", m_textzoom) ;
            editor.putInt("textsize", m_textsize) ;

            editor.commit();
        }
    }

    protected void openExternal( String url ) {
        MimeTypeMap map = MimeTypeMap.getSingleton();
        String ext = MimeTypeMap.getFileExtensionFromUrl(url);
        String type = map.getMimeTypeFromExtension(ext);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (type == null) {
            intent.setData(Uri.parse(url));
        }
        else {
            intent.setDataAndType(Uri.parse(url), type) ;
        }
        if( getPackageManager().resolveActivity(intent,0) != null ) {
            startActivity(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if ( getWebViewCount() + m_savedWeb.size() == 0 ) {
            try {
                File fpages = getFileStreamPath (pagesfile) ;
                int flen = (int)fpages.length() ;
                if( flen>0 ) {
                    byte[] buffer = new byte[flen];
                    FileInputStream inputStream = new FileInputStream(fpages);
                    if (inputStream != null) {
                        int r = inputStream.read(buffer);
                        if (r > 0) {
                            JSONArray ja = new JSONArray(new String(buffer, 0, r));
                            for (int i = 0; i < ja.length(); i++) {
                                newWeb(ja.getString(i));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        showWeb();
        fullscreen( false );
        fullscreen_delay(3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveOptions();

        JSONArray ja = new JSONArray();

        int i;
        for (i = 0; i < m_savedWeb.size(); i++) {
            WebEntry sw = m_savedWeb.get(i);
            ja.put( sw.getUrl() ) ;
        }

        for (i = 0; i < m_frame.getChildCount(); i++) {
            View v = m_frame.getChildAt(i);
            if (v instanceof WebView) {
                ja.put( ((WebView)v).getUrl() );
            }
        }

        try {
            FileOutputStream outputStream = openFileOutput( pagesfile, Context.MODE_PRIVATE);
            outputStream.write( ja.toString().getBytes() ) ;
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onDestroy() {
        try {
            FileOutputStream outputStream = openFileOutput("pages", Context.MODE_PRIVATE);
            outputStream.write( new JSONArray().toString().getBytes() ) ;
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }

    void traversView(View v, int level ) {
        Log.d("TRAVERS", "Level: " + level + ":" + v.toString());
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                traversView(((ViewGroup) v).getChildAt(i), level + 1);
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        int leftweb;
        if (level >= TRIM_MEMORY_BACKGROUND) {
            leftweb = 0;
        } else if (level >= TRIM_MEMORY_RUNNING_LOW) {
            leftweb = 1;
        } else {
            leftweb = 2;
        }

        for( int i=0; i<m_frame.getChildCount();  ) {
            if( getWebViewCount() <= leftweb  ) {
                break;
            }
            View v = m_frame.getChildAt(i);
            if (v instanceof WebView && v.getVisibility()!=View.VISIBLE ) {
                ((WebView) v).clearCache(false);
                WebEntry sw = new WebEntry();
                sw.save((WebView) v);
                m_savedWeb.add(sw);
                m_frame.removeView(v);
            }
            else {
                i++;
            }
        }
    }

    protected WebView newWebView() {
        WebView webView = new WebView(this) {
            boolean m_paused = false;

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                loadUrl("about:blank");
            }

            @Override
            protected void onVisibilityChanged(View changedView, int visibility) {
                super.onVisibilityChanged(changedView, visibility);
                if (visibility == View.VISIBLE) {
                    if (m_paused) {
                        m_paused = false;
                        onResume();
                    }
                } else {
                    if (!m_paused) {
                        m_paused = true;
                        onPause();
                    }
                }
            }
        };

        webView.setId(View.generateViewId());

        WebSettings webSettings = webView.getSettings();

        webSettings.setAppCacheEnabled(true);
        webSettings.setAppCachePath( getCacheDir().getPath() );
        webSettings.setDatabaseEnabled(true);
        if(Build.VERSION.SDK_INT < 19 ) {
            webSettings.setDatabasePath(getCacheDir().getPath());
        }
        webSettings.setDomStorageEnabled(true);
        webSettings.setGeolocationDatabasePath(getCacheDir().getPath());
        webSettings.setGeolocationEnabled(true);

        webSettings.setDefaultFontSize(m_textsize);
        webSettings.setDefaultFixedFontSize(m_textsize);
        webSettings.setTextZoom(m_textzoom) ;

        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setSupportZoom(true);
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        webSettings.setLoadWithOverviewMode(true);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
            // webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webSettings.setLoadsImagesAutomatically(m_loadimg);
        webSettings.setJavaScriptEnabled(m_jsen);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                // setProgress(newProgress * 100);
                if( view == currentWeb() )
                    setProgress();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, true);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (view == currentWeb()) {
                    setTitle(title);
                }
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                super.onReceivedIcon(view, icon);
                if (view == currentWeb()) {
                    setIcon(icon);
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);

                view.setBackgroundColor(0xff080808);
                m_frame.addView(view);

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

                getActionBar().hide();
                m_visibility =
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY ;
                m_frame.setSystemUiVisibility(m_visibility);
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();

                for(int i=m_frame.getChildCount()-1; i>0 ; i-- ) {
                    View view = m_frame.getChildAt(i);
                    if( view!=null && view instanceof WebView ) {
                        break;
                    }
                    m_frame.removeView(view);
                }

                setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED );
                fullscreen_delay(30);
            }

        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();
                if (scheme.equals("http")
                        || scheme.equals("https")
                        || scheme.equals("file")) {
                    if (uri.getHost().contains("google.") && uri.getPath().contains("search")) {
                        String q = uri.getQueryParameter("q");
                        if (q != null && q.length() > 5) {
                            uri = Uri.parse(q);
                            scheme = uri.getScheme();
                            if (scheme != null) {
                                if (scheme.equals("http")
                                        || scheme.equals("https")
                                        || scheme.equals("file")) {
                                    view.loadUrl(q);
                                    return true;
                                }
                            }
                        }
                    }
                    setTitle(url);
                    setIcon((Bitmap) null);
                    return false;
                } else {
                    openExternal(url);
                    return true;
                }
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                Log.i("hep-res", url);
                super.onLoadResource(view, url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if( view.getParent() == null && url.equals("about:blank") ) {
                    view.destroy();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
            }
        });

        m_frame.addView(webView);
        return webView;
    }

    private boolean m_connected = true;

    protected void newWeb(String url) {
        WebView v = newWebView();
        if (url == null) {
            url = defaultUrl ;

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null) {
                m_connected = ni.isConnected();
            } else {
                m_connected = false;
            }
            v.setNetworkAvailable(m_connected);
            if (!m_connected) {
                v.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);
            }
        }

        v.loadUrl(url);
        setTitle(url);
        setIcon((Bitmap) null);
        showWeb(v);

        invalidateOptionsMenu();
    }

    protected void closeWeb(int id) {
        for( int i= m_frame.getChildCount()-1; i>=0; i-- ) {
            View view = m_frame.getChildAt(i) ;
            if( (view instanceof WebView) && view.getId() == id ) {
                m_frame.removeView(view);
            }
        }

        for (int i = 0; i < m_savedWeb.size(); i++) {
            WebEntry we = m_savedWeb.get(i);
            if (we.id == id) {
                m_savedWeb.remove(we);
            }
        }

        invalidateOptionsMenu();
    }

    protected void closeWeb() {
        WebView view = currentWeb();
        if( view!=null ) {
            m_frame.removeView(view);
            if( (getWebViewCount() + m_savedWeb.size()) < 1 && defaultUrl.equals( view.getOriginalUrl() )) {
                super.onBackPressed();
                return ;
            }
        }
        showWeb();
        invalidateOptionsMenu();
    }

    protected void showWeb(WebView v) {
        for (int i = 0; i < m_frame.getChildCount(); i++) {
            View xv = m_frame.getChildAt(i);
            if (xv instanceof WebView && xv != v) {
                if( xv.getVisibility() == View.VISIBLE ) {
                    xv.setAlpha(0.0f);
                    xv.setVisibility(View.GONE);
                }
            }
        }
        if (v != null) {
            if( v!=currentWeb() ) {
                v.bringToFront();
                // changeble settings
                v.getSettings().setLoadsImagesAutomatically(m_loadimg);
                v.getSettings().setJavaScriptEnabled(m_jsen);
            }
            setTitle(v.getTitle());
            setIcon(v.getFavicon());
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1.0f);
            setProgress();
        }
    }

    protected void showWeb(WebEntry we) {
        if (m_savedWeb.contains(we)) {
            m_savedWeb.remove(we);
            WebView webView = newWebView();
            we.restore(webView);
            webView.setAlpha(0.0f);
            showWeb(webView);
        }
    }

    protected void showWeb(int id) {
        View v = m_frame.findViewById(id) ;
        if( v!=null && v instanceof WebView) {
            showWeb((WebView) v);
        }
        else {
            for (int i = 0; i < m_savedWeb.size(); i++) {
                WebEntry we = m_savedWeb.get(i);
                if (we.id == id) {
                    showWeb(we);
                    break;
                }
            }
        }
    }

    // show top web
    protected void showWeb() {
        if (currentWeb() == null) {
            if (m_savedWeb.size() > 0) {
                WebEntry we = m_savedWeb.get(m_savedWeb.size()-1);
                showWeb(we);
            }
            else {
                newWeb(null);
            }
        }
        else {
            showWeb(currentWeb());
        }
    }

    protected WebView currentWeb() {
        for (int i = m_frame.getChildCount() - 1; i >= 0; i--) {
            View v = m_frame.getChildAt(i);
            if (v instanceof WebView) {
                return (WebView) v;
            }
        }
        return null;
    }

    protected  int getWebViewCount()
    {
        int count=0;
        for( int i=0; i<m_frame.getChildCount(); i++ ) {
            if (m_frame.getChildAt(i) instanceof WebView) {
                count++;
            }
        }
        return count ;
    }

    protected void setIcon(Drawable icon) {
        getActionBar().setIcon(icon);
    }

    protected void setIcon(Bitmap icon) {
        setIcon(iconFromBitmap(icon));
    }

    protected Drawable iconFromBitmap(Bitmap icon) {
        int h = getActionBar().getHeight() * 3 / 4;
        if (icon == null) {
            Drawable dr;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dr = getResources().getDrawable(R.drawable.ic_browser, null);
            } else {
                dr = getResources().getDrawable(R.drawable.ic_browser);
            }
            if (dr instanceof BitmapDrawable) {
                icon = ((BitmapDrawable) dr).getBitmap();
            } else {
                return dr;
            }
        }
        if (h <= 0) h = 48;
        return new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(icon, h, h, false));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        WebView webView = currentWeb();
        if (webView == null) return;
        WebView.HitTestResult hit = webView.getHitTestResult();
        if (hit == null) {
            return;
        }
        String uri = hit.getExtra();
        if (uri == null || uri.length() < 4) {
            return;
        }
        int hittype = hit.getType();
        if (hittype == WebView.HitTestResult.UNKNOWN_TYPE) {
            return;
        }

        MenuItem mi;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));

        if (hittype == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            // direct open in new page
            mi = menu.add(0, 1001, 0, "Open in New Page");
            mi.setIntent(intent);
            // direct open external
            mi = menu.add(0, 1002, 0, "Open with Other App");
            mi.setIntent(intent);
            // copy link
            mi = menu.add(0, 1003, 0, "Copy Link");
            mi.setIntent(intent);
        } else if (hittype == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            // direct open image
            mi = menu.add(0, 1001, 0, "Open Image");
            mi.setIntent(intent);
            // direct open external
            mi = menu.add(0, 1002, 0, "Open Image with Other App");
            mi.setIntent(intent);
            // copy direct link
            mi = menu.add(0, 1003, 0, "Copy Image Link");
            mi.setIntent(intent);

            // delayed open in new page
            menu.add(0, 2001, 0, "Open in New Page");
            // delayed open external
            menu.add(0, 2002, 0, "Open with Other App");
            // delayed copy link
            menu.add(0, 2003, 0, "Copy Link");

        } else if (hittype == WebView.HitTestResult.IMAGE_TYPE) {
            // direct open image
            mi = menu.add(0, 1001, 0, "Open Image");
            mi.setIntent(intent);
            // direct open external
            mi = menu.add(0, 1002, 0, "Open Image with Other App");
            mi.setIntent(intent);
            // copy direct link
            mi = menu.add(0, 1003, 0, "Copy Image Link");
            mi.setIntent(intent);
        } else {
            // direct open external
            mi = menu.add(0, 2002, 0, "Open with Other App");
            mi.setIntent(intent);
            // direct copy link
            mi = menu.add(0, 2003, 0, "Copy Link");
            mi.setIntent(intent);
        }

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Intent intent;
        int id = item.getItemId();
        if (id == 1001) {
            // direct open in new page
            newWeb(item.getIntent().getDataString());
            return true;
        } else if (id == 1002) {
            // direct open external
            openExternal(item.getIntent().getDataString());
            return true;
        } else if (id == 1003) {
            // dirtect copy link
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "link", item.getIntent().getData()));
            return true;
        } else if (id >= 2000 && id < 2100) {
            // delayed open
            Message msg = m_handler.obtainMessage(MSG_OPENLINK, id, 0);
            currentWeb().requestFocusNodeHref(msg);
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }

    protected void fullscreen( boolean f ) {
        if (f) {
            getActionBar().hide();
            m_frame.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE );
        } else {
            getActionBar().show();
            m_frame.setSystemUiVisibility(0);
        }
    }

    protected void fullscreen_delay(long delayMillis) {
        m_handler.sendEmptyMessageDelayed(MSG_FULLSCREEN, delayMillis);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        if( m_handler.hasMessages(MSG_FULLSCREEN)) {
            m_handler.removeMessages(MSG_FULLSCREEN);
            fullscreen_delay(5000);
        }
    }

    abstract class ListEntry {
        String title ;
        String url ;
        Bitmap icon ;
        int listid ;
        abstract void tap() ;
        abstract void dismiss() ;
    } ;

    void setupScroll(final ListPopupWindow listPopup, final View scrollView, final int position) {

        scrollView.setOnTouchListener(new View.OnTouchListener() {

            Scroller scroller = new Scroller(getBaseContext());

            Runnable scrollUpdate = new Runnable() {
                @Override
                public void run() {
                    int sw = scrollView.getWidth();
                    int sx = scrollView.getScrollX();
                    if( sx>sw || sx<-sw) {
                        scroller.forceFinished(true);
                    }
                    if (scroller.computeScrollOffset()) {
                        scrollView.scrollTo( scroller.getCurrX(), 0);
                        scrollView.postOnAnimation(this);
                    }
                    else {
                        sx = scrollView.getScrollX();
                        if( sx > sw/2 || sx < -sw/2 ) {
                            // move out of range, close page and refresh list
                            ListAdapter la = listPopup.getListView().getAdapter();
                            if (la!=null && la instanceof ArrayAdapter) {
                                ListEntry pe = (ListEntry) scrollView.getTag();
                                pe.dismiss();
                                ((ArrayAdapter)la).remove(pe);
                            }
                        }
                    }
                }
            };

            void fling( int velocityX ) {
                int sx = scrollView.getScrollX();
                int wx = scrollView.getWidth();
                scroller.fling(sx, 0, velocityX/2, 0, -4*wx, 4*wx, 0, 0);
                int dx = scroller.getFinalX();
                int hwx = wx/2 ;
                if ( dx>-wx && dx <= -hwx ) {
                    scroller.setFinalX(-wx);
                } else if (dx >= hwx && dx< wx ) {
                    scroller.setFinalX(wx);
                } else if( dx > -hwx && dx < hwx ){
                    scroller.setFinalX(0);
                }
                if( scroller.getDuration() < 300 ) {
                    scroller.extendDuration(500 - scroller.getDuration() );
                }
                scrollView.postOnAnimation(scrollUpdate);
            }

            GestureDetector gestureDetector = new GestureDetector(getBaseContext(), new GestureDetector.SimpleOnGestureListener(){

                boolean xscroll ;

                @Override
                public boolean onDown(MotionEvent e) {
                    xscroll = false ;

                    return true ;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    ListEntry pe = (ListEntry) scrollView.getTag();
                    pe.tap();
                    listPopup.dismiss();
                    return true ;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if( !xscroll && position>0 ) {
                        if( Math.abs(distanceX) > Math.abs(distanceY) ) {
                            xscroll = true ;
                            scrollView.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }

                    if(xscroll) {
                        scrollView.scrollBy((int) distanceX, 0);
                    }
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if( xscroll ) {
                        fling( -(int)velocityX );
                        return true;
                    }
                    return false ;
                }
            });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean res = gestureDetector.onTouchEvent(event);
                if( !res ) {
                    int act = event.getActionMasked();
                    if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                        fling(0);
                        return true ;
                    }
                }
                return res  ;
            }
        });
    }

    void selectPage(View anchor) {
        final ListPopupWindow listPopup = new ListPopupWindow(this);

        if (anchor != null)
            listPopup.setAnchorView(anchor);

        class PageEntry extends  ListEntry {
            @Override
            void tap() {
                if( listid == 0 ) {
                    newWeb(null);
                }
                else {
                    showWeb(listid);
                }
            }

            @Override
            void dismiss() {
                closeWeb( listid );
            }
        }

        ArrayAdapter<ListEntry> pageadapter = new ArrayAdapter<ListEntry>(this,0) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ListEntry pe = getItem(position) ;
                View v = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.pageitem, parent, false);
                ((TextView)v.findViewById(R.id.pagetitle)).setText(pe.title);
                ((ImageView)v.findViewById(R.id.pageicon)).setImageDrawable(iconFromBitmap(pe.icon));
                v.setTag(pe);
                setupScroll(listPopup, v, position);
                return v;
            }

        };

        PageEntry pe ;
        pe = new PageEntry() ;
        pe.title = "Open New Page" ;

        Drawable dr = getResources().getDrawable( android.R.drawable.ic_input_add );
        if( dr instanceof BitmapDrawable ) {
            pe.icon = ((BitmapDrawable) dr).getBitmap();
        }
        else {
            pe.icon = null;
        }

        pe.listid = 0 ;
        pageadapter.add( pe );

        int savedpages = m_savedWeb.size();
        int i;
        for (i = m_frame.getChildCount() - 1; i >= 0; i--) {
            View v = m_frame.getChildAt(i);
            if ((v instanceof WebView )&& v != currentWeb()) {
                pe = new PageEntry();
                pe.title = ((WebView) v).getTitle();
                pe.icon = ((WebView) v).getFavicon();
                pe.listid = v.getId();
                pageadapter.add(pe);
            }
        }

        for (i = savedpages - 1; i >= 0; i--) {
            pe = new PageEntry();
            pe.title = m_savedWeb.get(i).getTitle();
            pe.icon = m_savedWeb.get(i).getIcon();
            pe.listid = m_savedWeb.get(i).id ;
            pageadapter.add(pe);
        }
        listPopup.setAdapter(pageadapter);

        listPopup.setContentWidth(m_frame.getWidth()*4/5);
        listPopup.setDropDownGravity(Gravity.END);
        listPopup.setModal(true);
        listPopup.show();

    }

    String backupId()
    {
        String id = null ;
        Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
        if (accounts.length > 0) {
            id = accounts[0].name ;
        }

        if( id == null || id.length()<3 ) {
            try {
                Method getString = Build.class.getDeclaredMethod("getString", String.class);
                getString.setAccessible(true);
                id = getString.invoke(null, "net.hostname").toString();
            } catch (Exception ex) {
                id = null;
            }
        }

        if( id==null || id.length()<3 ) {
            id = Build.MANUFACTURER + Build.DEVICE + Build.SERIAL ;
        }

        return id ;
    }

    void bookmarkBackup(JSONArray bookmarks) {
        new AsyncTask <JSONArray, Void, Void> () {
            @Override
            protected Void doInBackground(JSONArray... params) {
                try {
                    JSONObject bm = new JSONObject();
                    bm.put("timestamp", System.currentTimeMillis() );
                    bm.put("bookmarks", params[0]) ;
                    String id = backupId();
                    String bmstring = "https://sweltering-fire-2301.firebaseio.com/heples/" + URLEncoder.encode(id,"UTF-8") + ".json" ;
                    URL bmurl = new URL(bmstring);
                    HttpURLConnection bmConnection = (HttpURLConnection) bmurl.openConnection();
                    bmConnection.setDoOutput(true);
                    bmConnection.setRequestMethod("PUT");

                    OutputStream out = bmConnection.getOutputStream();
                    out.write( bm.toString().getBytes());

                    InputStream in = bmConnection.getInputStream();
                    byte [] content = new byte[512000] ;
                    int r = in.read(content) ;
                    String msg = new String(content,0, r);

                    msg = bmConnection.getResponseMessage();
                    bmConnection.disconnect();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute(bookmarks) ;
    }

    void bookmarkRestore() {

        JSONArray bm = bookmarkRead();
        if( bm.length()<= 0 ) {

            new AsyncTask<Void, Void, Void>() {

                JSONObject bm = null;

                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        String id = backupId();
                        String bmstring = "https://sweltering-fire-2301.firebaseio.com/heples/" + URLEncoder.encode(id, "UTF-8") + ".json";
                        URL bmurl = new URL(bmstring);
                        HttpURLConnection bmConnection = (HttpURLConnection) bmurl.openConnection();

                        String msg = bmConnection.getResponseMessage();
                        if (msg.equals("OK")) {
                            InputStream in = bmConnection.getInputStream();
                            byte[] content = new byte[256 * 1024];
                            int r = in.read(content);
                            if (r > 5) {
                                bm = new JSONObject(new String(content, 0, r));
                            }
                        }
                        bmConnection.disconnect();

                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    if (bm != null) {
                        try {
                            JSONArray bookmarks = bm.getJSONArray("bookmarks");
                            if (bookmarks != null && bookmarks.length() > 0) {
                                bookmarkSave(bookmarks);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }.execute();
        }
    }


    JSONArray bookmarkRead() {
        JSONArray bookmarks = new JSONArray();
        try {
            File fbookmark = getFileStreamPath(bookmarkfile);
            int flen = (int) fbookmark.length();
            if (flen > 0) {
                byte[] buffer = new byte[flen];
                FileInputStream inputStream = new FileInputStream(fbookmark);
                if (inputStream != null) {
                    int r = inputStream.read(buffer);
                    if (r > 0) {
                        bookmarks = new JSONArray(new String(buffer, 0, r));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch ( Exception e) {
            e.printStackTrace();
        }
        return bookmarks ;
    }

    void bookmarkSave( JSONArray bookmarks ) {
        if( bookmarks!=null ) {
            try {
                FileOutputStream fbookmark = openFileOutput(bookmarkfile, MODE_PRIVATE);
                if (fbookmark != null) {
                    fbookmark.write(bookmarks.toString().getBytes());
                    fbookmark.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            bookmarkBackup(bookmarks);
        }
    }

    void bookmarkAddCurrent( JSONArray bookmarks ) {
        JSONObject jo = new JSONObject();
        String url = currentWeb().getUrl() ;
        bookmarkRemove( bookmarks, url );
        try {
            jo.put("url", url );
            jo.put("title", currentWeb().getTitle());
            bookmarks.put(jo);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void bookmarkRemove( JSONArray bookmarks, String url ) {
        // to remove current bookmark
        for(int i = bookmarks.length()-1 ; i>=0 ; i-- ) {
            try {
                JSONObject jo = bookmarks.getJSONObject(i) ;
                String burl = jo.getString("url") ;
                if( burl.equals(url) ) {
                    bookmarks.remove(i) ;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    void selectBookmarks(View anchor) {
        final ListPopupWindow listPopup = new ListPopupWindow(this);

        if (anchor != null)
            listPopup.setAnchorView(anchor);

        ArrayAdapter< ListEntry > bmadapter = new ArrayAdapter<ListEntry>(this,0) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ListEntry pe = getItem(position) ;
                View v = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.pageitem, parent, false);
                ((TextView)v.findViewById(R.id.pagetitle)).setText(pe.title);
                ((ImageView)v.findViewById(R.id.pageicon)).setImageDrawable(iconFromBitmap(pe.icon));
                v.setTag(pe);
                setupScroll(listPopup, v, position);
                return v;
            }

        };

        class bm_t {
            JSONArray bookmark ;
            boolean dirty ;
        }
        final bm_t bm = new bm_t() ;
        bm.bookmark = bookmarkRead();
        bm.dirty = false ;

        class BookmarkEntry extends  ListEntry {
            @Override
            void tap() {
                if( url!=null && url.length()>4) {
                    currentWeb().loadUrl( this.url );
                }
            }

            @Override
            void dismiss() {
                bookmarkRemove(bm.bookmark, url) ;
                bm.dirty = true ;
            }
        }

        BookmarkEntry pe ;
        pe = new BookmarkEntry() {
            @Override
            void tap() {
                bookmarkAddCurrent( bm.bookmark ) ;
                bm.dirty = true ;
            }
        };
        pe.title = "Add Current Page" ;
        pe.listid = 0;

        Drawable dr = getResources().getDrawable( android.R.drawable.ic_input_add );
        if( dr instanceof BitmapDrawable ) {
            pe.icon = ((BitmapDrawable) dr).getBitmap();
        }
        else {
            pe.icon = null;
        }
        bmadapter.add( pe );

        for (int i = bm.bookmark.length() -1 ; i >= 0 ; i--) {
            try {
                JSONObject jo = bm.bookmark.getJSONObject(i);
                if (jo != null) {
                    pe = new BookmarkEntry();
                    pe.title = jo.getString("title");
                    pe.url = jo.getString("url");
                    bmadapter.add(pe);
                }
            }catch (JSONException e) {
                e.printStackTrace();
            }
        }

        listPopup.setAdapter(bmadapter);

        listPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if( bm.dirty ) {
                    bookmarkSave(bm.bookmark);
                }
            }
        });

        listPopup.setContentWidth(m_frame.getWidth()*4/5);
        listPopup.setDropDownGravity(Gravity.END);
        listPopup.setModal(true);
        listPopup.show();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_browser, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        WebView webview = currentWeb();
        if (webview != null) {
            MenuItem mi = menu.findItem(R.id.fullscreen);
            if (m_fullscreen) {
                mi.setIcon(R.drawable.ic_fullscreen_exit);
            }

            mi = menu.findItem(R.id.pages);
            int pagecount = m_savedWeb.size() + getWebViewCount();
            int ic_page = 0;
            if (pagecount > 0) {
                if (pagecount > 9) ic_page = ic_pages[10];
                else ic_page = ic_pages[pagecount];
            }
            mi.setIcon(ic_page);

            mi = menu.findItem(R.id.fullscreen);
            mi.setChecked(m_fullscreen);

            mi = menu.findItem(R.id.disablejavascript);
            mi.setChecked(!m_jsen);

            mi = menu.findItem(R.id.noimage);
            mi.setChecked(!m_loadimg);

            // history
            WebBackForwardList wfl = webview.copyBackForwardList();
            if (wfl != null && wfl.getSize() > 0) {
                SubMenu shis = menu.findItem(R.id.history).getSubMenu();
                if (shis != null) {
                    shis.removeGroup(R.id.group_history);
                    for (int m = wfl.getSize() - 1; m >= 0; m--) {
                        WebHistoryItem whi = wfl.getItemAtIndex(m);
                        if (whi != null) {
                            mi = shis.add(R.id.group_history, 0, 0, whi.getUrl());
                            mi.setIcon(iconFromBitmap(whi.getFavicon()));
                        }
                    }
                }
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();
        int gid = item.getGroupId();

        //noinspection SimplifiableIfStatement
        if (gid == R.id.group_history) {
            // history
            String url = item.getTitle().toString();
            currentWeb().loadUrl(item.getTitle().toString());
            setTitle(url);
            setIcon(item.getIcon());
            return true;
        } else if (id == R.id.pages) {
            View anchor = null ;
            int resId = getResources().getIdentifier("action_bar_container", "id", "android");
            if( resId > 0) {
                anchor = findViewById(resId) ;
            }
            selectPage(anchor);
            return true;
        } else if (id == R.id.bookmarks) {
            View anchor = null ;
            int resId = getResources().getIdentifier("action_bar_container", "id", "android");
            if( resId > 0) {
                anchor = findViewById(resId) ;
            }
            selectBookmarks(anchor);
            return true;
        }
        else if (id == R.id.close_page) {
            closeWeb();
            return true;
        } else if (id == R.id.new_page) {
            newWeb(null);
            return true;
        } else if (id == R.id.fullscreen) {
            m_fullscreen = !m_fullscreen;
            fullscreen_delay(100);
            return true;
        } else if (id == R.id.noimage) {
            m_loadimg = !m_loadimg;
            currentWeb().getSettings().setLoadsImagesAutomatically(m_loadimg);
            currentWeb().reload();
            return true;
        } else if (id == R.id.disablejavascript) {
            m_jsen = !m_jsen;
            currentWeb().getSettings().setJavaScriptEnabled(m_jsen);
            currentWeb().reload();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (currentWeb().canGoBack()) {
            currentWeb().goBack();
        } else {
            closeWeb();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle bundle;

        int idx = 0;
        int i;
        for (i = 0; i < m_savedWeb.size(); i++) {
            WebEntry sw = m_savedWeb.get(i);
            bundle = sw.getState();
            if (bundle != null) {
                outState.putBundle("WebView" + idx, bundle);
                idx++;
            }
        }

        for (i = 0; i < m_frame.getChildCount(); i++) {
            View v = m_frame.getChildAt(i);
            if (v instanceof WebView) {
                WebView webView = (WebView) v;
                bundle = new Bundle();
                webView.saveState(bundle);
                outState.putBundle("WebView" + idx, bundle);
                idx++;
            }
        }

        outState.putInt("WebViewCount", idx);

    }

}
