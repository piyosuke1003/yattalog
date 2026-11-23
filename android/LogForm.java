package jp.personal.yattalog;

import android.content.*;
import android.database.Cursor;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.FrameLayout;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONObject;

final class LogForm {
    private static LogForm instance;
    static LogForm get(Context context) {
        if (instance == null) instance = new LogForm(context.getApplicationContext());
        return instance;
    }
    final WebView view;
    MainActivity activity;
    private final Context app;
    private final MutableContextWrapper context;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean floating;
    private LogForm(Context app) {
        this.app=app;
        context=new MutableContextWrapper(app);
        view=new WebView(context);
        view.setBackgroundColor(0xfffaf8f6);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setAllowContentAccess(false);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setBlockNetworkLoads(true);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public void onPageFinished(WebView v, String url) { displayMode(); }
        });
        view.addJavascriptInterface(new Bridge(),"AndroidLog");
        view.loadUrl("file:///android_asset/index.html");
    }
    void attach(Context target, FrameLayout host, boolean compact) {
        activity=target instanceof MainActivity ? (MainActivity)target : null;
        floating=compact;
        context.setBaseContext(target);
        if (view.getParent()!=host) {
            detach();
            host.addView(view,new FrameLayout.LayoutParams(-1,-1));
        }
        displayMode();
    }
    private void displayMode() { view.evaluateJavascript("window.setFloating && setFloating("+floating+")",null); }
    void detach() {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
    }
    void releaseActivity(MainActivity old) {
        if (activity==old) { detach(); activity=null; context.setBaseContext(app); }
    }
    void releaseOverlay() { if (activity==null) { detach(); context.setBaseContext(app); } }
    final class Bridge {
        @JavascriptInterface public void floatButton() { ui.post(() -> { if (activity!=null) activity.beginFloating(); }); }
        @JavascriptInterface public void fold() { ui.post(OverlayService::foldPanel); }
        @JavascriptInterface public void stopFloating() { ui.post(OverlayService::exitToApp); }
        @JavascriptInterface public void save(String title,long start,long end,String summary) {
            ui.post(() -> {
                Runnable save=() -> worker.execute(() -> {
                    try {
                        if(title==null || title.trim().isEmpty() || title.length()>200 || end<=start || end-start>86400000L)
                            throw new IllegalArgumentException("タイトルと所要時間を確認してください。");
                        insert(title.trim(),start,end);
                        ui.post(() -> result(true,summary));
                    } catch (Exception e) {
                        String msg=e instanceof IllegalArgumentException ? e.getMessage() : "保存できませんでした。端末のカレンダー権限と同期設定を確認してください。";
                        ui.post(() -> result(false,msg));
                    }
                });
                if (activity!=null) activity.withCalendar(save); else save.run();
            });
        }
    }
    void result(boolean ok,String message) {
        view.evaluateJavascript("window.onCalendarResult("+ok+","+JSONObject.quote(message)+")",
            unused -> { if(ok && floating) OverlayService.foldPanel(); });
    }
    private long target() {
        String where=Calendars.ACCOUNT_TYPE+"=? AND "+Calendars.CALENDAR_ACCESS_LEVEL+">=? AND "+Calendars.SYNC_EVENTS+"=1";
        long target=-1;
        try(Cursor c=app.getContentResolver().query(Calendars.CONTENT_URI,new String[]{Calendars._ID,Calendars.CALENDAR_DISPLAY_NAME},where,
                new String[]{"com.google",String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR)},null)) {
            while(c!=null && c.moveToNext()) {
                String name=c.getString(1);
                if(name==null || !"やったことリスト".equals(name.replaceAll("[\\s　]+",""))) continue;
                if(target>=0) throw new IllegalArgumentException("同名のカレンダーが複数あります。保存先を特定できません。");
                target=c.getLong(0);
            }
        }
        if(target<0) throw new IllegalArgumentException("Google カレンダーの設定で「やったこと リスト」の同期をオンにしてください。");
        return target;
    }
    private void insert(String title,long start,long end) {
        long calendar=target();
        String where=Events.CALENDAR_ID+"=? AND "+Events.TITLE+"=? AND "+Events.DTSTART+"=? AND "+Events.DTEND+"=? AND "+Events.DELETED+"=0 AND "+Events.ALL_DAY+"=0";
        try(Cursor c=app.getContentResolver().query(Events.CONTENT_URI,new String[]{Events._ID},where,
                new String[]{String.valueOf(calendar),title,String.valueOf(start),String.valueOf(end)},null)) {
            if(c!=null && c.moveToFirst()) return;
        }
        ContentValues v=new ContentValues();
        v.put(Events.CALENDAR_ID,calendar); v.put(Events.TITLE,title);
        v.put(Events.DTSTART,start); v.put(Events.DTEND,end);
        v.put(Events.EVENT_TIMEZONE,TimeZone.getDefault().getID());
        if(app.getContentResolver().insert(Events.CONTENT_URI,v)==null) throw new IllegalStateException();
    }
}
