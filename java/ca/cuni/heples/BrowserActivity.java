package ca.cuni.heples;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.backup.BackupManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
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
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.MimeTypeMap;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.ProgressBar;
import android.widget.Scroller;
import android.widget.ShareActionProvider;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE;

public class BrowserActivity extends Activity {

    protected ViewGroup m_frame;
    protected Handler m_handler;

    // browser options
    protected boolean m_fullscreen = false;
    protected int m_textsize = 18 ;
    protected int m_textzoom = 120 ;

    protected int m_saveOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ;
    protected View m_customView = null ;
    protected WebChromeClient.CustomViewCallback m_customViewCB = null ;

    protected boolean m_datachanged = false ;

    protected final int MSG_FULLSCREEN = 1001;
    protected final int MSG_OPENLINK = 1002;
    protected final int MSG_OPENPAGESMENU = 1003;

    static final int Permit_Reload = 100 ;

    protected final static String bookmarkfile = "bookmarks" ;
    protected final static String defaultUrl = "https://www.google.com/";

    static class VideoItem {
        public String url ;
        public String type ;
        public VideoItem(String u, String t ){
            url = u;
            type = t ;
        }
    }
    // protected ArrayList <VideoItem> videolist = new ArrayList <VideoItem> ();

    protected final int[] ic_pages = {
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

    protected BookmarkArray m_bookmarks ;

    private class SitesOpenHelper extends SQLiteOpenHelper {

        static final int DATABASE_VERSION = BuildConfig.VERSION_CODE ;
        static final String DATABASE_NAME = "sites";
        static final String SITES_TABLE_NAME = "sites";
        static final String SITE_NAME = "site";
        static final String SITE_UPDATE = "upd";
        static final String SITE_NOJS = "nojs";
        static final String SITE_NOIMG = "noimg";
        static final String sites_create_table =
                "CREATE TABLE IF NOT EXISTS " + SITES_TABLE_NAME + " (" +
                        SITE_NAME + " TEXT PRIMARY KEY , " +
                        SITE_UPDATE + " INT , " +
                        SITE_NOJS + " BOOLEAN, " +
                        SITE_NOIMG + " BOOLEAN " + ");";

        SitesOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(sites_create_table);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int i, int i1) {
            db.execSQL("DROP TABLE IF EXISTS "+SITES_TABLE_NAME);
            db.execSQL(sites_create_table);
        }

        public void setSiteProp( String site, boolean nojs, boolean noimg ) {
            int xl = site.lastIndexOf('/') ;
            if( xl>4 ) {
                site = site.substring(0,xl);
                SQLiteDatabase db = getWritableDatabase();
                String where ;
                if( (!nojs) && (!noimg) ) {
                    where = SITE_NAME + " = '" + site + "'";
                    db.delete( SITES_TABLE_NAME, where, null );
                }
                else {
                    ContentValues values = new ContentValues();
                    values.put(SITE_NAME, site);
                    values.put(SITE_NOJS, nojs);
                    values.put(SITE_NOIMG, noimg);
                    long upd = Calendar.getInstance().getTimeInMillis();
                    values.put(SITE_UPDATE, upd);
                    db.replace(SITES_TABLE_NAME, null, values);

                    // remove all old
                    where = SITE_UPDATE + " < " + (upd - (100L * 24 * 3600 * 1000)) ;
                    db.delete( SITES_TABLE_NAME, where, null );
                }
                m_datachanged = true ;
            }
        }

        public Bundle getSiteProp( String site ) {
            int xl = site.lastIndexOf('/') ;
            if( xl>4 ) {
                site = site.substring(0,xl);
                SQLiteDatabase db = getWritableDatabase();
                String[] columns = {
                        SITE_NOJS, SITE_NOIMG
                };
                String where = SITE_NAME + " = '" + site + "'" ;
                Cursor cursor = db.query(true,
                        SITES_TABLE_NAME,
                        columns,
                        where,
                        null,
                        null,
                        null,
                        null,
                        null);
                if (cursor.moveToFirst()) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(SITE_NOJS, cursor.getInt(0) != 0 );
                    bundle.putBoolean(SITE_NOIMG, cursor.getInt(1) != 0 );
                    cursor.close();

                    ContentValues values = new ContentValues();
                    long upd = Calendar.getInstance().getTimeInMillis();
                    values.put(SITE_UPDATE, upd);
                    db.update(SITES_TABLE_NAME, values, where, null);

                    return bundle ;
                }
            }
            return null;
        }

    }

    SitesOpenHelper sites ;

    void setSitePref() {
        WebView v = currentWeb();
        if( v != null && sites!=null ) {
            boolean nojs = ! v.getSettings().getJavaScriptEnabled();
            boolean noimg = ! v.getSettings().getLoadsImagesAutomatically();
            sites.setSiteProp(v.getUrl(), nojs, noimg);
        }
    }

    class WebEntry {
        final private int     id  ;
        final private Bundle state ;
        final private String url ;
        final private String title ;
        final private Bitmap icon ;

        public WebEntry(Bundle st) {
            url = st.getString("url");
            title = st.getString("title");
            state = st.getBundle("state");
            icon = null ;
            id = View.generateViewId();
        }

        public WebEntry(WebView w) {
            id = w.getId();
            url = w.getUrl();
            title = w.getTitle();
            icon = w.getFavicon();
            state = new Bundle();
            w.saveState(state);
        }

        void restore(WebView v) {
            if (state != null) {
                v.restoreState(state);
            }
            else if(url!=null) {
                v.loadUrl(url);
            }
            v.setId(id);
            if( currentWeb()==v) {
                if (title != null) {
                    setTitle(title);
                }
                setIcon(icon);
            }
        }

        Bundle getBundle() {
            Bundle bd = new Bundle();
            if( url != null )
                bd.putString("url", url);
            if( title != null )
                bd.putString("title", title);
            if( state != null )
                bd.putBundle("state", state);
            return bd ;
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
    }

    protected ArrayList<WebEntry> m_savedWeb = new ArrayList<>();

    protected WebViewClient webViewCli = new WebViewClient() {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            boolean x_nojs = !view.getSettings().getJavaScriptEnabled();
            boolean x_noimg = !view.getSettings().getLoadsImagesAutomatically();

            boolean nojs = false ;
            boolean noimg = false ;

            Bundle b = sites.getSiteProp(url);
            if( b!=null ) {
                nojs = b.getBoolean(SitesOpenHelper.SITE_NOJS) ;
                noimg = b.getBoolean(SitesOpenHelper.SITE_NOIMG) ;
            }
            if( nojs != x_nojs || noimg!=x_noimg ) {
                view.getSettings().setJavaScriptEnabled(!nojs);
                view.getSettings().setLoadsImagesAutomatically(!noimg);
            }

            view.setTag(R.id.videoList,null);
            if( view == currentWeb() ) {
                setTitle(url);
                setIcon(favicon);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (scheme.equals("http")
                    || scheme.equals("https")
                    || scheme.equals("content")
                    || scheme.equals("file")) {
                if (uri.getHost().contains(".google.") && uri.getPath().contains("search")) {
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
                WebView cv = currentWeb();
                if( view != cv ) {
                    Uri cu = Uri.parse( cv.getUrl() );
                    if( uri.getHost().equals(cu.getHost()) ) { // from same host?
                        showWeb(view);
                    }
                }
                return false;
            } else {
                openExternal(url);
                return true;
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if( !request.isRedirect() && !request.hasGesture() ) {
                    String url = view.getUrl();
                    if( url==null || url.equals("about:blank")) {
                        closeWeb(view);
                        return true;
                    }
                }
            }
            return shouldOverrideUrlLoading(view, request.getUrl().toString());
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if( ! isReload ) {
                WebBackForwardList wfl = view.copyBackForwardList();
                if (wfl != null && wfl.getSize() > 1 ) {
                    int curIdx = wfl.getCurrentIndex();
                    WebHistoryItem whi = wfl.getItemAtIndex(curIdx - 1);
                    if (whi != null) {
                        String xurl = whi.getUrl();
                        int xl , ul ;
                        String x_path="", x_file="" ;
                        String u_path="", u_file="" ;

                        xl = xurl.lastIndexOf('/') ;
                        if( xl>0 ) {
                            x_path = xurl.substring(0, xl);
                            x_file = xurl.substring(xl + 1);
                        }

                        ul = url.lastIndexOf('/');
                        if( ul>0 ) {
                            u_path = url.substring(0, ul);
                            u_file = url.substring(ul + 1);
                        }

                        if( xl == ul && x_path.equals(u_path) && (!x_file.equals(u_file)) && !x_file.isEmpty() && !u_file.isEmpty() ) {
                            if( m_bookmarks.has(xurl) ) {
                                m_bookmarks.remove(xurl);
                                m_bookmarks.add( url, view.getTitle() );
                            }
                        }
                    }
                }
            }
            super.doUpdateVisitedHistory(view, url, isReload);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            String type ;
            // String type = map.getMimeTypeFromExtension(ext);
            String ext = MimeTypeMap.getFileExtensionFromUrl(url);
            if( ext!=null && ext.length()>0 ) {
                if( ext.equalsIgnoreCase("mp4") || ext.equalsIgnoreCase("m3u8") || ext.equalsIgnoreCase("mkv") ) {
                    type = "video/*" ;
                }
                else if( ext.equalsIgnoreCase("ts") ) {     // ts clip , don't list it
                    type = ext ;
                }
                else {
                    String Eurl ;
                    try {
                        Eurl = URLEncoder.encode(url, "UTF-8");

                    } catch (UnsupportedEncodingException e) {
                        Eurl = url ;
                        e.printStackTrace();
                    }
                    type = URLConnection.guessContentTypeFromName(Eurl);
                }
                if( type != null && type.startsWith("video") ) {
                    VideoItem vi = new VideoItem(url, type);

                    List <VideoItem> vl = (List<VideoItem>) view.getTag(R.id.videoList);
                    if( vl == null ) {
                        vl = new ArrayList<>();
                        view.setTag(R.id.videoList, vl);
                    }
                    else {
                        for( int i=vl.size()-1; i>=0 ; i-- ) {
                            VideoItem v = vl.get(i);
                            if( url.equals( v.url ) ) {
                                vl.remove(i);
                            }
                        }
                    }
                    vl.add(vi);
                }
            }
            super.onLoadResource(view, url);
        }


        @Override
        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            final HttpAuthHandler fhandler = handler ;
            final EditText txtUser = new EditText(view.getContext());
            final EditText txtPass = new EditText(view.getContext());
            txtUser.setHint("Username");
            txtPass.setHint("Password");
            LinearLayout ly = new LinearLayout(view.getContext());
            ly.setOrientation(LinearLayout.VERTICAL);
            ly.addView(txtUser);
            ly.addView(txtPass);

            new AlertDialog.Builder(view.getContext())
                    .setTitle("Auth Required")
                    .setView(ly)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            String user = txtUser.getText().toString();
                            String pass = txtPass.getText().toString();
                            fhandler.proceed(user, pass);
                        }
                    })
                    .show();
            // super.onReceivedHttpAuthRequest(view, handler, host, realm);
        }
    };

    protected WebChromeClient webChromeCli = new WebChromeClient() {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            String msg = consoleMessage.message();
            String src = consoleMessage.sourceId();

            Log.i("WebConsole", src+":"+msg);

            return super.onConsoleMessage(consoleMessage);
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            // setProgress(newProgress * 100);
            if( view == currentWeb() )
                setProgress();
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            if( isUserGesture && !isDialog ) {
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView());
                resultMsg.sendToTarget();
                showWeb(view);
                return true;
            }
            return false ;
        }

        @Override
        public void onCloseWindow(WebView window) {
            super.onCloseWindow(window);
            closeWeb(window);
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback geoCallback) {
            requestPermission(Manifest.permission.ACCESS_FINE_LOCATION , Permit_Reload );
            geoCallback.invoke(origin, true, true);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (view == currentWeb()) {
                setTitle(title);
                String url = view.getUrl();
                if( m_bookmarks.has(url) ) {
                    String xtitle = m_bookmarks.getTitle(url);
                    if( !xtitle.equals(title) ) {
                        m_bookmarks.add(url, title);
                    }
                }
                setShareIntent();
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
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            super.onShowCustomView(view, callback);

            m_customView = view ;
            m_customViewCB = callback ;

            m_customView.setBackgroundColor(0xff080808);
            m_saveOrientation = getRequestedOrientation();
            m_frame.addView(m_customView);

            setFullscreen();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }


        @Override
        public void onHideCustomView() {
            super.onHideCustomView();

            if( m_customView != null ) {
                m_frame.removeView(m_customView);
                m_customView = null;
                m_customViewCB = null;
            }
            setRequestedOrientation( m_saveOrientation );

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

    };

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setContentView(R.layout.activity_browser);
        loadOptions();

        getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);

        setContentView(R.layout.activity_browser);
        m_frame = findViewById(R.id.main_frame);

        m_bookmarks = new BookmarkArray();

        m_handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if(msg==null)
                    return;
                super.handleMessage(msg);
                if (msg.what == MSG_FULLSCREEN) {
                    if( !m_handler.hasMessages(MSG_FULLSCREEN) )
                        setFullscreen();
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
                        if ( m_customView == null ) {
                            if( (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0 ) {
                                getActionBar().hide();
                            }
                            else {
                                getActionBar().show();
                                m_frame.setSystemUiVisibility(0);
                                if (m_fullscreen) {
                                    fullscreen_delay(5000);
                                }
                            }
                        }
                    }
                });


        // sites preference
        sites = new SitesOpenHelper(this);

        if (savedInstanceState != null) {
            m_savedWeb.clear();
            int tabcount = savedInstanceState.getInt("wvc", 0);
            int i;
            for (i = 0; i < tabcount; i++) {
                Bundle state = savedInstanceState.getBundle("wv" + i);
                if (state != null) {
                    WebEntry sw = new WebEntry(state);
                    m_savedWeb.add(sw);
                }
            }
        }

        Intent intent = getIntent();
        if (intent != null) {
            String url = intent.getDataString();
            if (url != null) {
                newWeb(url);
            }
        }
        // bookmarkRestore();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = intent.getDataString();
        if (url != null) {
            newWeb(url);
        }
    }

    private void setProgress() {
        WebView view = currentWeb();
        if( view!=null && view.getVisibility() == View.VISIBLE ) {
            int progress = view.getProgress() ;
            ProgressBar pbar = (ProgressBar) findViewById(R.id.progressBar) ;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
                pbar.setProgress(progress,true);
            }
            else {
                pbar.setProgress(progress);
            }
            float alpha = pbar.getAlpha() ;
            if( progress>5 && progress<95 ) {
                if( alpha < 0.6f ) {
                    pbar.animate().setDuration(300).alpha(0.6f);
                }
            }
            else {
                if( alpha > 0.0f ) {
                    pbar.animate().setDuration(300).alpha(0.0f);
                }
            }
        }
    }

    private void loadOptions() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        m_fullscreen = prefs.getBoolean("fullscreen", false);
        m_textzoom = prefs.getInt("textzoom", 115) ;
        m_textsize = prefs.getInt("textsize", 18) ;
    }

    private void saveOptions() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        boolean x_fullscreen = prefs.getBoolean("fullscreen", false);
        int x_textzoom = prefs.getInt("textzoom", 115) ;
        int x_textsize = prefs.getInt("textsize", 18) ;

        if( x_fullscreen != m_fullscreen ||
                x_textzoom != m_textzoom ||
                x_textsize != m_textsize
        ) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("fullscreen", m_fullscreen);
            editor.putInt("textzoom", m_textzoom) ;
            editor.putInt("textsize", m_textsize) ;

            editor.apply();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if( requestCode == Permit_Reload ) {
            WebView v = currentWeb();
            v.reload();
        }
    }

    protected void requestPermission( String perm , int permitOp ) {
        if( checkSelfPermission( perm ) != PackageManager.PERMISSION_GRANTED ) {
            requestPermissions( new String[]{perm}, permitOp ) ;
        }
    }

    protected void openExternal( String url , String type ) {
        // MimeTypeMap map = MimeTypeMap.getSingleton();
        // String ext = MimeTypeMap.getFileExtensionFromUrl(url);
        // String type = map.getMimeTypeFromExtension(ext);
        if( type == null ) {
            openExternal( url );
        }
        else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndTypeAndNormalize(Uri.parse(url), type);
            if( getPackageManager().resolveActivity(intent,0) != null ) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.d("openExternal", e.getMessage());
                }
            }
        }
    }

    protected void clearData() {
        WebViewDatabase wvd = WebViewDatabase.getInstance(this);
        wvd.clearFormData();
        wvd.clearHttpAuthUsernamePassword();
        WebStorage ws = WebStorage.getInstance();
        ws.deleteAllData();
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();        // clear cache
        WebView cv = currentWeb();
        if( cv!=null ) {
            cv.clearCache(true);
        }
    }

    protected String getUrlType( String url ) {
        String type = null;
        try {
            if( url != null )
                type = URLConnection.guessContentTypeFromName(URLEncoder.encode(url, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
        }
        if( type == null ) {
            type = "text/html";
        }
        return type ;
    }

    protected void openExternal( String url ) {

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("link", url));

        Intent intent = new Intent(Intent.ACTION_VIEW);
        // MimeTypeMap map = MimeTypeMap.getSingleton();
        // String ext = MimeTypeMap.getFileExtensionFromUrl(url);
        // String type = map.getMimeTypeFromExtension(ext);

        String type = null;
        try {
            type = URLConnection.guessContentTypeFromName(URLEncoder.encode(url, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if( type == null ) {
            intent.setDataAndNormalize(Uri.parse(url));
        }
        else {
            intent.setDataAndTypeAndNormalize(Uri.parse(url), type);
        }
        if( getPackageManager().resolveActivity(intent,0) != null ) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Log.d("openExternal", e.getMessage());
            }
        }
    }

    protected void sharePage( String url ) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, url);
        sendIntent.setType("*/*");
        startActivity(sendIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        showWeb();
    }

    @Override
    protected void onResume() {
        super.onResume();

        setFullscreen();

        WebView v = currentWeb() ;
        if( v!=null ) v.resumeTimers();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if( m_customView != null && m_customViewCB != null ) {
            m_customViewCB.onCustomViewHidden();
        }

        WebView v = currentWeb();
        if( v!=null ) {
            v.pauseTimers();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        saveOptions();

        if( m_bookmarks.save()) {
            m_datachanged = true ;
        }

        if( m_datachanged  ) {
            BackupManager backup = new BackupManager(this);
            backup.dataChanged();
            m_datachanged = false ;
        }
    }

    @Override
    public void onTrimMemory(int level) {
        View v ;
        super.onTrimMemory(level);

        int leftweb;
        if (level >= TRIM_MEMORY_BACKGROUND) {
            leftweb = 0;
        } else if (level >= TRIM_MEMORY_RUNNING_LOW) {
            leftweb = 1;
        } else {
            leftweb = 2;
        }

        for( int i=m_frame.getChildCount()-1; i>=0; i--  ) {
            if( getWebViewCount() <= leftweb  ) {
                break;
            }
            v = m_frame.getChildAt(i);
            if (v instanceof WebView && v.getVisibility()!=View.VISIBLE ) {
                WebEntry sw = new WebEntry((WebView) v);
                m_savedWeb.add(sw);
                m_frame.removeView(v);
                ((WebView) v).clearCache(false);
                ((WebView) v).destroy();
            }
        }
    }

    protected WebView newWebView() {
        WebView webView = new WebView(this) {
            private boolean m_paused = false;

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

        webView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT ));
        webView.setId(View.generateViewId());

        WebSettings webSettings = webView.getSettings();

        webSettings.setAppCacheEnabled(true);
        webSettings.setAppCachePath( getCacheDir().getPath() );

        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setGeolocationEnabled(true);

        webSettings.setDefaultFontSize(m_textsize);
        webSettings.setDefaultFixedFontSize(m_textsize);
        webSettings.setTextZoom(m_textzoom) ;

        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        // webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        webSettings.setBuiltInZoomControls(true);
        webSettings.setSupportZoom(true);
        webSettings.setDisplayZoomControls(false);

        webSettings.setSupportMultipleWindows(true);

        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // all true on new web
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setJavaScriptEnabled(true);

        webView.setDownloadListener( new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Intent i = new Intent(Intent.ACTION_VIEW , Uri.parse(url));
                startActivity(i);
            }
        });

        webView.setWebViewClient(webViewCli);
        webView.setWebChromeClient(webChromeCli);

        m_frame.addView(webView);
        return webView;
    }

    protected void newWeb(String url) {
        WebView v = newWebView();

        if (url == null) {
            url = defaultUrl ;
        }
        else {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (scheme.equals("file")) {
                requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Permit_Reload );
            }
        }

        v.loadUrl(url);
        setTitle(url);
        setIcon((Bitmap) null);
        showWeb(v);
    }

    protected void closeWeb(int id) {
        for( int i= m_frame.getChildCount()-1; i>=0; i-- ) {
            View view = m_frame.getChildAt(i) ;
            if( (view instanceof WebView) && view.getId() == id ) {
                m_frame.removeView(view);
                ((WebView) view).destroy();
            }
        }

        for (int i = 0; i < m_savedWeb.size(); i++) {
            WebEntry we = m_savedWeb.get(i);
            if (we.id == id) {
                m_savedWeb.remove(we);
            }
        }

        if ( currentWeb() == null && m_savedWeb.size() == 0 ) {
            finish();
        }
        else {
            showWeb();
        }
    }

    // close web page
    protected void closeWeb(WebView view) {
        if( view !=  null)
            closeWeb(view.getId());
    }

    // close current web page
    protected void closeWeb() {
        closeWeb(currentWeb());
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
            }
            setTitle(v.getTitle());
            setIcon(v.getFavicon());
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1.0f);
            setProgress();
            invalidateOptionsMenu();
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
        if(v instanceof WebView) {
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
            dr = getResources().getDrawable(R.drawable.ic_browser, null);
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

        if (hittype == SRC_ANCHOR_TYPE) {
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
            // delayed open in new page
            menu.add(0, 2001, 0, "Open in New Page");
            // delayed open external
            menu.add(0, 2002, 0, "Open with Other App");
            // delayed copy link
            menu.add(0, 2003, 0, "Copy Link");

            // direct open image
            mi = menu.add(0, 1001, 0, "Open Image");
            mi.setIntent(intent);
            // direct open external
            mi = menu.add(0, 1002, 0, "Open Image with Other App");
            mi.setIntent(intent);
            // copy direct link
            mi = menu.add(0, 1003, 0, "Copy Image Link");
            mi.setIntent(intent);

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
        int id = item.getItemId();
        if (id == 1001) {
            // direct open in new page
            newWeb(item.getIntent().getDataString());
            return true;
        } else if (id == 1002) {
            // direct open external
            openExternal( item.getIntent().getDataString());
            return true;
        } else if (id == 1003) {
            // dirtect copy link
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText( "link", item.getIntent().getDataString());
            clipboard.setPrimaryClip(cd);
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

    protected void setFullscreen() {
        if( m_customView == null ) {
            if (m_fullscreen) {
                getActionBar().hide();
                m_frame.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_IMMERSIVE);
            } else {
                getActionBar().show();
                m_frame.setSystemUiVisibility(0);
            }
        }
        else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

            getActionBar().hide();
            m_customView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY );
        }
    }

    protected void fullscreen_delay(long delayMillis) {
        if( m_handler.hasMessages(MSG_FULLSCREEN)) {
            m_handler.removeMessages(MSG_FULLSCREEN);
        }
        m_handler.sendEmptyMessageDelayed(MSG_FULLSCREEN, delayMillis);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if( m_fullscreen && m_handler.hasMessages(MSG_FULLSCREEN) )
            fullscreen_delay(5000);
    }

    static abstract class ListEntry {
        String title ;
        String url ;
        Bitmap icon ;
        int listid ;
        abstract void tap() ;
        abstract void dismiss() ;
    }

    void setupScroll(final ListPopupWindow listPopup, final View scrollView, final int position) {

        scrollView.setOnTouchListener(new View.OnTouchListener() {

            final Scroller scroller = new Scroller(getBaseContext());

            final Runnable scrollUpdate = new Runnable() {
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
                            if (la instanceof ArrayAdapter) {
                                ListEntry pe = (ListEntry) scrollView.getTag();
                                pe.dismiss();
                                ((ArrayAdapter) la).remove(pe);
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

            final GestureDetector gestureDetector = new GestureDetector(getBaseContext(), new GestureDetector.SimpleOnGestureListener(){

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

    /*
    String backupId()
    {
        String id = null ;

        try {
            Class c = Class.forName("android.os.SystemProperties");
            Method getProp = c.getDeclaredMethod("get", String.class);
            id = (String) getProp.invoke(null, "net.hostname");
        } catch (Exception ex) {
            id = null;
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
    */


    class BookmarkArray {
        JSONArray bookmarks ;
        boolean dirty ;

        BookmarkArray() {
            dirty = false ;
            bookmarks = new JSONArray();
            try {
                File exFiles = getExternalFilesDir (null);
                File fBookmark = new File( exFiles, bookmarkfile );
                if( !fBookmark.exists() ) {
                    File backupDir = Environment.getExternalStorageDirectory();
                    File fBackupBookmark = new File(backupDir, "backup/bookmarks");
                    if (fBackupBookmark.canRead()) {
                        long fLen = fBackupBookmark.length();
                        if( fLen>0 ){
                            byte[] buffer = new byte[(int)fLen];
                            FileInputStream inputStream = new FileInputStream(fBackupBookmark);
                            int r = inputStream.read(buffer);
                            if (r > 0) {
                                FileOutputStream o = new FileOutputStream(fBookmark);
                                o.write(buffer);
                                o.close();
                            }
                        }
                    }
                }

                if( fBookmark.canRead() ) {
                    long fLen = fBookmark.length();
                    if( fLen>0 ){
                        byte[] buffer = new byte[(int)fLen];
                        FileInputStream inputStream = new FileInputStream(fBookmark);
                        int r = inputStream.read(buffer);
                        if (r > 0) {
                            bookmarks = new JSONArray(new String(buffer, 0, r));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int getIdx( String url ) {
            for(int i = 0; i<bookmarks.length() ; i++ ) {
                try {
                    JSONObject jo = bookmarks.getJSONObject(i) ;
                    String burl = jo.getString("url") ;
                    if( burl.equals(url) ) {
                        return i ;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return -1 ;
        }

        boolean has( String url ) {
            return getIdx(url) >= 0 ;
        }

        String getTitle( String url ) {
            for(int i = 0; i<bookmarks.length() ; i++ ) {
                try {
                    JSONObject jo = bookmarks.getJSONObject(i) ;
                    String burl = jo.getString("url") ;
                    if( burl.equals(url) ) {
                        return jo.getString("title");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return null ;
        }

        void remove( String url ){
            int idx = getIdx(url) ;
            if( idx>=0 ) {
                bookmarks.remove(idx) ;
                dirty = true ;
            }
        }

        void add( String url, String title) {
            try {
                JSONObject jo = new JSONObject();
                jo.put("url", url );
                jo.put("title", title);
                int idx = getIdx(url) ;
                if( idx>=0 ) {
                    bookmarks.put( idx, jo ) ;
                }
                else {
                    bookmarks.put(jo);
                }
                dirty = true ;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        void addCurrent() {
            add(currentWeb().getUrl(), currentWeb().getTitle());
        }

        boolean save() {
            if( dirty ) {
                try {
                    File exFiles = getExternalFilesDir (null);
                    // FileOutputStream fbookmark = openFileOutput(bookmarkfile, MODE_PRIVATE);
                    FileOutputStream outPut = new FileOutputStream(new File( exFiles, bookmarkfile ));
                    outPut.write(bookmarks.toString().getBytes());
                    outPut.close();
                    return true ;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // bookmarkBackup(bookmarks);
                dirty = false ;
            }
            return false ;
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

        class BookmarkEntry extends  ListEntry {
            @Override
            void tap() {
                if( url!=null && url.length()>4) {
                    currentWeb().loadUrl( this.url );
                }
            }

            @Override
            void dismiss() {
                m_bookmarks.remove(url);
            }
        }

        BookmarkEntry pe ;
        pe = new BookmarkEntry() {
            @Override
            void tap() {
                m_bookmarks.addCurrent();
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

        for (int i = m_bookmarks.bookmarks.length() -1 ; i >= 0 ; i--) {
            try {
                JSONObject jo = m_bookmarks.bookmarks.getJSONObject(i);
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
        listPopup.setContentWidth(m_frame.getWidth()*4/5);
        listPopup.setDropDownGravity(Gravity.END);
        listPopup.setModal(true);
        listPopup.show();
    }

    private Menu optionsMenu ;

    private void setShareIntent() {
        if( optionsMenu != null) {
            MenuItem mi = optionsMenu.findItem(R.id.share);
            ShareActionProvider shareProvider = (ShareActionProvider) mi.getActionProvider();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            String url = currentWeb().getUrl();
            shareIntent.putExtra(Intent.EXTRA_TEXT, url);
            shareIntent.setType(getUrlType(url));
            shareProvider.setShareIntent(shareIntent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_browser, menu);
        optionsMenu = menu ;
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

            boolean jsen = webview.getSettings().getJavaScriptEnabled();
            mi = menu.findItem(R.id.disablejavascript);
            mi.setChecked(!jsen);

            boolean loadimg = webview.getSettings().getLoadsImagesAutomatically();
            mi = menu.findItem(R.id.noimage);
            mi.setChecked(!loadimg);

            // history
            SubMenu mhis = menu.findItem(R.id.history).getSubMenu();
            if (mhis != null) {
                mhis.removeGroup(R.id.group_history);
            }

            setShareIntent();
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
            WebView v = currentWeb();
            if( v != null ) {
                if (id == 0) {
                    v.reload();
                }
                else if( v.canGoBackOrForward( id )) {
                    setIcon(item.getIcon());
                    setTitle(item.getTitle().toString());
                    v.goBackOrForward(id);
                }
            }
            // currentWeb().loadUrl(item.getTitle().toString());
            // setTitle(url);
            // setIcon(item.getIcon());
            return true;
        }
        else if (gid == R.id.group_video) {
            List<VideoItem> vl = (List<VideoItem>) currentWeb().getTag(R.id.videoList);
            if( vl != null ) {
                openExternal( vl.get(id).url, vl.get(id).type );
            }
        }
        else if (id == R.id.pages) {
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
        }
        else if (id == R.id.new_page) {
            newWeb(null);
            return true;
        }
        else if (id == R.id.fullscreen) {
            m_fullscreen = !m_fullscreen;
            setFullscreen();
            return true;
        }
        else if (id == R.id.noimage) {
            currentWeb().getSettings().setLoadsImagesAutomatically(item.isChecked());
            setSitePref() ;
            currentWeb().reload();
            return true;
        }
        else if (id == R.id.disablejavascript) {
            currentWeb().getSettings().setJavaScriptEnabled(item.isChecked());
            setSitePref() ;
            currentWeb().reload();
            return true;
        }
        else if( id == R.id.history) {
            SubMenu mhis = item.getSubMenu();
            if( mhis != null ) {
                WebView webview = currentWeb();
                WebBackForwardList wfl = webview.copyBackForwardList();
                mhis.removeGroup(R.id.group_history);
                int curIdx = wfl.getCurrentIndex();
                for (int m = wfl.getSize() - 1; m >= 0; m--) {
                    WebHistoryItem whi = wfl.getItemAtIndex(m);
                    if (whi != null) {
                        String title = whi.getTitle() ;
                        if( title == null ) {
                            title = whi.getUrl();
                        }
                        if( m==curIdx) {
                            title = "* "+title ;
                        }
                        MenuItem mi = mhis.add(R.id.group_history, m - curIdx, 0, title);
                        mi.setIcon(iconFromBitmap(whi.getFavicon()));
                    }
                }
            }
        }
        else if (id == R.id.openinother) {
            openExternal( currentWeb().getUrl() ) ;
            return true;
        }
        else if (id == R.id.cleardata) {
            clearData();
            return true;
        }
        else if (id == R.id.openvideo) {
            SubMenu mvid = item.getSubMenu();
            if( mvid != null ) {
                mvid.removeGroup(R.id.group_video);
                List<VideoItem> vl = (List<VideoItem>) currentWeb().getTag(R.id.videoList);
                if( vl != null )
                    for( int v = 0 ; v<vl.size(); v++ ) {
                        try {
                            URL url = new URL(vl.get(v).url);
                            String s = url.getHost() + url.getPath() ;
                            int l = s.length();
                            if( l > 30 ) {
                                s = s.substring(0,15) + ".." + s.substring( l-12 );
                            }
                            mvid.add(R.id.group_video, v, 0, s);
                        }
                        catch (Exception e) {
                            Log.d("openVideo", e.getMessage());
                        }
                    }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (currentWeb().canGoBack()) {
            currentWeb().goBack();
        } else {
            closeWeb() ;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Bundle bundle;
        if(outState==null)
            return;

        int idx = 0;
        int i;
        for (i = 0; i < m_savedWeb.size(); i++) {
            WebEntry we = m_savedWeb.get(i);
            bundle = we.getBundle();
            if (bundle != null) {
                outState.putBundle("wv" + idx, bundle);
                idx++;
            }
        }

        for (i = 0; i < m_frame.getChildCount(); i++) {
            View v = m_frame.getChildAt(i);
            if (v instanceof WebView) {
                WebEntry we = new WebEntry((WebView)v);
                bundle = we.getBundle();
                if (bundle != null) {
                    outState.putBundle("wv" + idx, bundle);
                    idx++;
                }
            }
        }

        outState.putInt("wvc", idx);
        super.onSaveInstanceState(outState);
    }
}
