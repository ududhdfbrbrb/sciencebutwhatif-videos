package com.sbwi.autoupload;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/** Hourly background check: tells you when a post fails or nothing is scheduled any more. */
public class WatchJob extends JobService {
    static final int ID = 4711;

    public static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!new Prefs(c).watch()) { js.cancel(ID); return; }
        JobInfo ji = new JobInfo.Builder(ID, new ComponentName(c, WatchJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60 * 60 * 1000L)
                .setPersisted(true)
                .build();
        try { js.schedule(ji); } catch (Exception ignored) {}
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { check(getApplicationContext()); } catch (Exception ignored) {}
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }

    static void check(Context c) throws Exception {
        Prefs p = new Prefs(c);
        if (!p.ready()) return;
        Mcp m = p.mcp();
        JSONObject failed = m.call("list_posts", Mcp.args("status", "failed", "limit", 20));
        JSONArray arr = failed.optJSONArray("posts");
        String seen = p.seenFailed();
        StringBuilder now = new StringBuilder();
        int fresh = 0;
        String firstText = "";
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String id = o.optString("id");
            now.append(id).append(',');
            if (!seen.contains(id)) { fresh++; if (firstText.isEmpty()) firstText = o.optString("baseContent"); }
        }
        p.set("seenFailed", now.toString());
        if (fresh > 0 && !seen.isEmpty()) notify(c, 1, fresh + " post" + (fresh > 1 ? "s" : "") + " failed", firstText);

        JSONObject sch = m.call("list_posts", Mcp.args("status", "scheduled", "limit", 1));
        if (sch.optInt("total", 1) == 0) {
            boolean paused = false;
            try {
                GitHub.FileData f = p.gh().get("control.json");
                if (f != null) paused = new JSONObject(f.text).optBoolean("paused");
            } catch (Exception ignored) {}
            long nowMs = System.currentTimeMillis();
            if (!paused && nowMs - p.lastStuckAlert() > 3 * 3600_000L) {
                p.setLong("stuck", nowMs);
                notify(c, 2, "Nothing scheduled", "The auto upload has no posts queued right now. It may be at the daily limit, or a run failed.");
            }
        }
    }

    static void notify(Context c, int id, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("watch", "Upload alerts", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "watch")
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try { nm.notify(id, n); } catch (SecurityException ignored) {}
    }
}
