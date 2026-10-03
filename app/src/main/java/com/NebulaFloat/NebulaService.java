package com.NebulaFloat;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

public class NebulaService extends Service {
    private WindowManager wm;
    private FrameLayout root;
    private WindowManager.LayoutParams lp;
    private WebView web;
    private boolean bubble = false;
    private final int BG = Color.rgb(5,7,10);

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(77, notification());
        showWindow();
    }

    private Notification notification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "nebula")
                .setContentTitle("NEBULA")
                .setContentText("Floating utility is running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26)
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(new NotificationChannel(
                    "nebula","NEBULA",NotificationManager.IMPORTANCE_LOW));
    }

    private int type() {
        return Build.VERSION.SDK_INT >= 26 ?
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
            WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showWindow() {
        if (!Settings.canDrawOverlays(this)) return;

        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new NativeBridge(), "NebulaNative");
        web.loadUrl("file:///android_asset/index.html");

        root.addView(web, new FrameLayout.LayoutParams(-1,-1));

        lp = new WindowManager.LayoutParams(
                dp(330), dp(500), type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(18); lp.y = dp(90);

        try { wm.addView(root, lp); } catch(Exception e) { stopSelf(); }
    }

    private int dp(int x) { return (int)(x*getResources().getDisplayMetrics().density + .5f); }

    public void resize(int w, int h) {
        if (root == null || bubble) return;
        lp.width = Math.max(dp(260), w);
        lp.height = Math.max(dp(260), h);
        wm.updateViewLayout(root, lp);
    }

    public void minimize() {
        if (root == null || bubble) return;
        bubble = true;
        lp.width = dp(64); lp.height = dp(64);
        web.loadUrl("javascript:nebulaMinimized(true)");
        wm.updateViewLayout(root, lp);
    }

    public void restore() {
        if (root == null || !bubble) return;
        bubble = false;
        lp.width = dp(330); lp.height = dp(500);
        web.loadUrl("javascript:nebulaMinimized(false)");
        wm.updateViewLayout(root, lp);
    }

    public void close() {
        if (root != null) wm.removeView(root);
        root = null; stopSelf();
    }

    public class NativeBridge {
        @JavascriptInterface public void resizeWindow(int w,int h) {
            new Handler(Looper.getMainLooper()).post(() -> resize(w,h));
        }
        @JavascriptInterface public void minimizeWindow() {
            new Handler(Looper.getMainLooper()).post(() -> minimize());
        }
        @JavascriptInterface public void restoreWindow() {
            new Handler(Looper.getMainLooper()).post(() -> restore());
        }
        @JavascriptInterface public void closeWindow() {
            new Handler(Looper.getMainLooper()).post(() -> close());
        }
        @JavascriptInterface public String shell(String command) {
            return runShell(command);
        }
    }

    private String runShell(String command) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh","-c",command});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String line;
            while ((line=r.readLine()) != null) out.append(line).append("\n");
            while ((line=e.readLine()) != null) out.append(line).append("\n");
            p.waitFor();
        } catch(Exception ex) { out.append("nebula: ").append(ex.getMessage()).append("\n"); }
        return out.toString();
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent i,int f,int id) { return START_STICKY; }
}
