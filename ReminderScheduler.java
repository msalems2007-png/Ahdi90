package com.ahdi.ninety.app;
import android.app.*;import android.content.*;import java.util.*;
public final class ReminderScheduler {
 private ReminderScheduler(){}
 public static void schedule(Context c){
  AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  Intent in=new Intent(c,ReminderReceiver.class);
  PendingIntent pi=PendingIntent.getBroadcast(c,77,in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Calendar cal=Calendar.getInstance(); int h=c.getSharedPreferences(AppData.PREF,0).getInt("rem_hour",7); int m=c.getSharedPreferences(AppData.PREF,0).getInt("rem_min",0); cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,m);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0); if(cal.before(Calendar.getInstance())) cal.add(Calendar.DAY_OF_YEAR,1);
  am.setInexactRepeating(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),AlarmManager.INTERVAL_DAY,pi);
 }
}
