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
    android.content.SharedPreferences prefs = c.getSharedPreferences(NativeBackend.accountPreferences(BuildConfig.SERVER_URL), 0);
    boolean enabled = prefs.getBoolean("alerts", false);
    JSONObject trip = me.optJSONObject("trip");
    prefs
        .edit()
        .putString("alert_trip", trip == null ? "" : trip.optString("endsAt"))
        .putString("alert_ticket", me.optString("claimReadyAt"))
        .apply();
    for (int id : new int[] {1, 2}) {
      String date =
          id == 1 && trip != null
              ? trip.optString("endsAt")
              : id == 2 ? me.optString("claimReadyAt") : "";
      Intent i = new Intent(c, NativeAlerts.class).putExtra("id", id);
      PendingIntent p =
          PendingIntent.getBroadcast(
              c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      alarms.cancel(p);
      if (!enabled || date.isEmpty()) {
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);
        continue;
      }
      try {
        long at = java.time.Instant.parse(date).toEpochMilli();
        if (at > System.currentTimeMillis())
          alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        else if (!date.equals(prefs.getString("alert_sent_" + id, "")))
          new NativeAlerts().onReceive(c, i.putExtra("date", date));
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  public void onReceive(Context c, Intent i) {
    android.content.SharedPreferences prefs = c.getSharedPreferences(NativeBackend.accountPreferences(BuildConfig.SERVER_URL), 0);
    if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())
        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) {
      try {
        schedule(
            c,
            new JSONObject()
                .put("claimReadyAt", prefs.getString("alert_ticket", ""))
                .put("trip", new JSONObject().put("endsAt", prefs.getString("alert_trip", ""))));
      } catch (Exception ignored) {
      }
      return;
    }
    if (!prefs.getBoolean("alerts", false)) return;
    if (Build.VERSION.SDK_INT >= 33
        && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return;
    NotificationManager m = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26)
      m.createNotificationChannel(
          new NotificationChannel("gmm_return", "수비대 소식", NotificationManager.IMPORTANCE_DEFAULT));
    int id = i.getIntExtra("id", 1);
    String due = prefs.getString(id == 1 ? "alert_trip" : "alert_ticket", "");
    if (due.isEmpty() || due.equals(prefs.getString("alert_sent_" + id, ""))) return;
    Intent open =
        new Intent(c, MainActivity.class)
            .putExtra("destination", id == 1 ? "expedition" : "draw")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent p =
        PendingIntent.getActivity(
            c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b =
        Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(c, "gmm_return")
            : new Notification.Builder(c);
    m.notify(
        id,
        b.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("갈매미맨 방범대")
            .setContentText(id == 1 ? "탐험대가 돌아왔어요. 귀환 보상을 받아 주세요." : "지원서를 받을 수 있어요.")
            .setContentIntent(p)
            .setAutoCancel(true)
            .build());
    prefs.edit().putString("alert_sent_" + id, due).apply();
  }
}
