package com.ahdi.ninety.app;

import android.app.*;import android.content.*;import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CH="ahdi_reminders";
    @Override public void onReceive(Context c, Intent i){
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){ NotificationChannel ch=new NotificationChannel(CH,"تذكيرات عهدي",NotificationManager.IMPORTANCE_DEFAULT); nm.createNotificationChannel(ch); }
        Intent open=new Intent(c,MainActivity.class); open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,10,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CH):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("عَهدي 90 🌅").setContentText("صباح الخير — هل ستضع علامة اليوم؟").setAutoCancel(true).setContentIntent(pi);
        if(Build.VERSION.SDK_INT<33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==0) nm.notify(90,b.build());
    }
}
