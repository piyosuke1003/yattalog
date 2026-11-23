package jp.personal.yattalog;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public class OverlayService extends Service {
    private static final String STOP="jp.personal.yattalog.STOP";
    private static OverlayService active;
    private WindowManager wm;
    private Context windowContext;
    private FrameLayout panel;
    private Button bubble;
    private WindowManager.LayoutParams small;
    private LogForm form;
    private int type;
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        type=Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        windowContext=this;
        if(Build.VERSION.SDK_INT>=30) {
            android.hardware.display.DisplayManager dm=getSystemService(android.hardware.display.DisplayManager.class);
            windowContext=createDisplayContext(dm.getDisplay(Display.DEFAULT_DISPLAY)).createWindowContext(type,null);
        }
        wm=windowContext.getSystemService(WindowManager.class);
        form=LogForm.get(this);
    }
    @Override public int onStartCommand(Intent i,int flags,int startId) {
        if(i==null || STOP.equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; }
        try {
            startForeground(1,notification());
            if(!Settings.canDrawOverlays(this)) throw new SecurityException();
            if(bubble==null) makeBubble();
            active=this;
            if(form.activity!=null) form.activity.moveTaskToBack(true);
        } catch(RuntimeException e) {
            Toast.makeText(this,"丸いボタンを表示できません。端末の表示権限を確認してください。",Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }
    private Notification notification() {
        Notification.Builder b;
        if(Build.VERSION.SDK_INT>=26) {
            String channel="floating";
            getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(channel,"丸いボタン",NotificationManager.IMPORTANCE_LOW));
            b=new Notification.Builder(this,channel);
        } else b=new Notification.Builder(this);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,OverlayService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return b.setSmallIcon(R.drawable.log_icon).setContentTitle("やったこと").setContentText("丸いボタンを表示中")
                .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"常駐を終了",stop).build();
    }
    private Point area() {
        Point size=new Point();
        wm.getDefaultDisplay().getSize(size);
        return size;
    }
    private WindowManager.LayoutParams params(int w,int h,int flags) {
        return new WindowManager.LayoutParams(w,h,type,flags,PixelFormat.TRANSLUCENT);
    }
    private void makeBubble() {
        int side=dp(52);
        bubble=new Button(windowContext);
        bubble.setText("＋"); bubble.setTextSize(24); bubble.setTextColor(-1);
        bubble.setPadding(0,0,0,0); bubble.setContentDescription("やったことを記録");
        GradientDrawable bg=new GradientDrawable(); bg.setShape(GradientDrawable.OVAL); bg.setColor(0xff74465b);
        bubble.setBackground(bg); bubble.setElevation(dp(4));
        small=params(side,side,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        small.gravity=Gravity.TOP|Gravity.LEFT;
        Point screen=area(); small.x=screen.x-side-dp(12); small.y=screen.y-side-dp(24);
        bubble.setOnClickListener(v -> showPanel());
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX,downY; int x,y; boolean dragging;
            public boolean onTouch(View v,MotionEvent e) {
                switch(e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX=e.getRawX(); downY=e.getRawY(); x=small.x; y=small.y; dragging=false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx=Math.round(e.getRawX()-downX),dy=Math.round(e.getRawY()-downY);
                        if(!dragging && Math.hypot(dx,dy)<ViewConfiguration.get(v.getContext()).getScaledTouchSlop()) return true;
                        dragging=true; small.x=x+dx; small.y=y+dy; boundBubble(); return true;
                    case MotionEvent.ACTION_UP: if(!dragging) v.performClick(); return true;
                    default: return true;
                }
            }
        });
        wm.addView(bubble,small);
    }
    private void boundBubble() {
        Point p=area();
        small.x=Math.max(0,Math.min(small.x,p.x-small.width));
        small.y=Math.max(0,Math.min(small.y,p.y-small.height));
        wm.updateViewLayout(bubble,small);
    }
    private WindowManager.LayoutParams panelParams() {
        Point p=area();
        WindowManager.LayoutParams lp=params(Math.min(dp(370),p.x-dp(32)),Math.min(dp(550),p.y-dp(48)),WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        lp.gravity=Gravity.CENTER;
        lp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        return lp;
    }
    private void showPanel() {
        if(panel!=null) return;
        panel=new FrameLayout(windowContext);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xfffaf8f6); bg.setCornerRadius(dp(16));
        panel.setBackground(bg); panel.setClipToOutline(true);
        try {
            wm.addView(panel,panelParams());
            form.attach(windowContext,panel,true);
            bubble.setVisibility(View.GONE);
        } catch(RuntimeException e) { stopSelf(); }
    }
    static void foldPanel() { if(active!=null) active.hidePanel(); }
    private void hidePanel() {
        if(panel==null) return;
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(panel.getWindowToken(),0);
        form.releaseOverlay();
        if(panel.isAttachedToWindow()) wm.removeViewImmediate(panel);
        panel=null;
        if(bubble!=null) bubble.setVisibility(View.VISIBLE);
    }
    static void exitToApp() { if(active!=null) { active.hidePanel(); active.stopSelf(); } }
    @Override public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if(bubble!=null && bubble.isAttachedToWindow()) boundBubble();
        if(panel!=null) wm.updateViewLayout(panel,panelParams());
    }
    @Override public void onDestroy() {
        hidePanel();
        if(bubble!=null && bubble.isAttachedToWindow()) wm.removeViewImmediate(bubble);
        if(active==this) active=null;
        stopForeground(true);
        super.onDestroy();
    }
}
