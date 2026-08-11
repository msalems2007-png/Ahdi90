package com.ahdi.ninety.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class AppData {
    public static final String PREF = "ahdi90";
    public static final String START = "start_date";
    private AppData() {}
    public static SharedPreferences p(Context c){ return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    public static LocalDate start(Context c){
        String s=p(c).getString(START,null);
        if(s==null){ LocalDate d=LocalDate.now().plusDays(1); p(c).edit().putString(START,d.toString()).apply(); return d; }
        return LocalDate.parse(s);
    }
    public static int dayIndex(Context c, LocalDate d){ return (int)ChronoUnit.DAYS.between(start(c),d)+1; }
    public static boolean validDay(int i){ return i>=1 && i<=90; }
    public static boolean marked(Context c,int i){ return p(c).getBoolean("day_"+i,false); }
    public static void setMarked(Context c,int i,boolean v){ p(c).edit().putBoolean("day_"+i,v).apply(); }
    public static int total(Context c){ int n=0; for(int i=1;i<=90;i++) if(marked(c,i)) n++; return n; }
    public static int streak(Context c){
        int n=0; int cur=dayIndex(c,LocalDate.now()); if(cur<1) cur=1; if(cur>90) cur=90; for(int i=cur;i>=1;i--){ if(marked(c,i)) n++; else break; } return n;
    }
    public static boolean weekComplete(Context c,int week){
        int a=(week-1)*7+1,b=Math.min(week*7,90); if(week>12) return false;
        for(int i=a;i<=b;i++) if(!marked(c,i)) return false; return true;
    }
}
