package com.example.electronicqueue.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.electronicqueue.R;

public class NotificationHelper {

    private static final String CHANNEL_ID = "queue_channel";

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Electronic Queue",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о наступлении очереди");
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    public static void showTurnNotification(Context ctx, int ticket) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ваша очередь подошла")
                .setContentText("Подойдите к окну. Талон №" + ticket)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(10000 + ticket, b.build());
    }
}
