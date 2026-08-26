package com.unbox.nsearch.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.unbox.nsearch.IndexController;
import com.unbox.nsearch.R;

import java.lang.ref.WeakReference;

/**
 * 索引前台服务：仅负责常驻通知 + Wakelock。
 * 真正的索引逻辑在 {@link IndexController} 单例中，故进程存活期间进度持续，App 重开也能续传。
 */
public class IndexingService extends Service {

    public static final String ACTION_START = "com.unbox.nsearch.action.START";
    private static final int NOTIF_ID = 1001;
    private static final String CHANNEL_ID = "nsearch_indexing";
    /** 通知刷新间隔（毫秒）。 */
    private static final long TICKER_INTERVAL_MS = 500L;

    private NotificationManager nm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        IndexController.get(this).setHost(this);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.indexing_notification_title),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            startForeground(NOTIF_ID, buildNotification());
            startTicker();
        }
        return START_STICKY;
    }

    private void startTicker() {
        ticker = new Runnable() {
            @Override
            public void run() {
                if (nm != null) nm.notify(NOTIF_ID, buildNotification());
                handler.postDelayed(this, TICKER_INTERVAL_MS);
            }
        };
        handler.postDelayed(ticker, TICKER_INTERVAL_MS);
    }

    private void stopTicker() {
        if (ticker != null) handler.removeCallbacks(ticker);
    }

    private Notification buildNotification() {
        IndexController.State s = IndexController.get(this).getState();
        String text = getString(R.string.indexing_notification_text,
                s.indexed, s.total, s.currentFile);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.indexing_notification_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_popup_sync) // 系统同步图标（避免额外资源依赖）
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    /** 由 IndexController 在索引结束后调用，停止前台并结束服务。 */
    public void stopForegroundService() {
        stopTicker();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        IndexController.get(this).clearHost(this);
        stopTicker();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
