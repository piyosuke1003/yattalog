package jp.personal.yattalog;

import android.Manifest;
import androidx.activity.ComponentActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends ComponentActivity {
    private FrameLayout host;
    private Runnable pending;
    private boolean waitingOverlay;
    private LogForm form;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        host = new FrameLayout(this);
        setContentView(host);
        form = LogForm.get(this);
        waitingOverlay = state != null && state.getBoolean("waitingOverlay");
    }
    @Override public void onResume() {
        super.onResume();
        if (OverlayService.collapseForLauncher()) {
            moveTaskToBack(true);
            return;
        }
        OverlayService.exitToApp();
        form.attach(this, host, false);
        if (waitingOverlay) {
            waitingOverlay = false;
            if (Settings.canDrawOverlays(this)) startBubble();
            else toast("丸いボタンを使うには「他のアプリの上に表示」を許可してください。");
        }
    }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putBoolean("waitingOverlay", waitingOverlay);
        super.onSaveInstanceState(state);
    }
    void withCalendar(Runnable next) {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED) {
            next.run(); return;
        }
        pending = next;
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR}, 7);
    }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] grants) {
        super.onRequestPermissionsResult(code,names,grants);
        if (code != 7 || pending == null) return;
        Runnable next = pending; pending = null;
        if (grants.length == 2 && grants[0]==0 && grants[1]==0) next.run();
        else form.result(false,"端末の設定でカレンダーへのアクセスを許可してください。");
    }
    void beginFloating() {
        withCalendar(() -> {
            if (Settings.canDrawOverlays(this)) { startBubble(); return; }
            try {
                waitingOverlay = true;
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
            } catch (RuntimeException e) { waitingOverlay=false; toast("端末の設定で「他のアプリの上に表示」を許可してください。"); }
        });
    }
    private void startBubble() {
        Intent intent = new Intent(this,OverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        } catch (RuntimeException e) { toast("丸いボタンを開始できませんでした。"); }
    }
    void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override public void onDestroy() {
        if (pending != null) form.result(false, "カレンダーの権限を確認して、もう一度お試しください。");
        form.releaseActivity(this);
        super.onDestroy();
    }
}
