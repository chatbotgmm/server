package org.dpdns.gmm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;

public final class NativeAlerts extends BroadcastReceiver {
  static void schedule(Context c, JSONObject me) {
    AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    boolean enabled = c.getSharedPreferences("native_account", 0).getBoolean("alerts", false);
    for (int id : new int[] {1, 2}) {
      JSONObject trip = me.optJSONObject("trip");
      String date =
          id == 1 && trip != null
              ? trip.optString("endsAt")
              : id == 2 ? me.optString("claimReadyAt") : "";
      Intent i = new Intent(c, NativeAlerts.class).putExtra("id", id);
      PendingIntent p =
          PendingIntent.getBroadcast(
              c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      alarms.cancel(p);
      if (!enabled || date.isEmpty()) continue;
      try {
        long at = java.time.Instant.parse(date).toEpochMilli();
        if (at > System.currentTimeMillis())
          alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60000, p);
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void onReceive(Context c, Intent i) {
    if (!c.getSharedPreferences("native_account", 0).getBoolean("alerts", false)) return;
    if (Build.VERSION.SDK_INT >= 33
        && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return;
    NotificationManager m = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26)
      m.createNotificationChannel(
          new NotificationChannel("gmm_return", "수비대 소식", NotificationManager.IMPORTANCE_DEFAULT));
    Intent open = new Intent(c, MainActivity.class);
    PendingIntent p =
        PendingIntent.getActivity(
            c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b =
        Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(c, "gmm_return")
            : new Notification.Builder(c);
    int id = i.getIntExtra("id", 1);
    m.notify(
        id,
        b.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("갈매미맨 수비대")
            .setContentText(id == 1 ? "탐험대가 돌아왔어요. 귀환 보상을 받아 주세요." : "뽑기권을 받을 수 있어요.")
            .setContentIntent(p)
            .setAutoCancel(true)
            .build());
  }
}
