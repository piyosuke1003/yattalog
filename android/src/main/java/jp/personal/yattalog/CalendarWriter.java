package jp.personal.yattalog;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import java.util.TimeZone;

final class CalendarWriter {
    private final Context app;
    CalendarWriter(Context context) { app = context.getApplicationContext(); }
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
    void insert(String title,long start,long end) {
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
