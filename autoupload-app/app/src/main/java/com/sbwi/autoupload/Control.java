package com.sbwi.autoupload;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** control.json in the videos repo — read by both hourly tasks at the start of every run. */
public class Control {
    public JSONObject json;
    String sha;

    static JSONObject defaults() throws Exception {
        return new JSONObject("{"
                + "\"paused\":false,"
                + "\"daily_limit\":15,"
                + "\"quiet_hours\":{\"enabled\":false,\"from\":2,\"to\":7},"
                + "\"extra_hashtags\":\"\","
                + "\"whatif\":{\"enabled\":true,\"per_slot\":2,\"queue\":[],\"blocked\":[]},"
                + "\"country\":{\"enabled\":true,\"per_slot\":2,\"ranking_queue\":[],\"blocked\":[],"
                + "\"formats\":{\"shape\":true,\"capital\":true,\"landmark\":true,\"food\":true,\"facts\":true,\"animals\":true,\"ranking2\":true}}"
                + "}");
    }

    public static Control load(GitHub gh) throws Exception {
        Control c = new Control();
        GitHub.FileData f = gh.get("control.json");
        c.json = defaults();
        if (f != null) {
            c.sha = f.sha;
            JSONObject cur = new JSONObject(f.text);
            java.util.Iterator<String> it = cur.keys();
            while (it.hasNext()) { String k = it.next(); c.json.put(k, cur.get(k)); }
        }
        return c;
    }

    public void save(GitHub gh, String msg) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        json.put("updated", f.format(new Date()) + " (phone)");
        try {
            gh.put("control.json", json.toString(2) + "\n", sha, msg);
        } catch (Exception e) {
            // file changed since we loaded it (e.g. a task consumed a queued topic): reload sha and retry once
            GitHub.FileData cur = gh.get("control.json");
            gh.put("control.json", json.toString(2) + "\n", cur == null ? null : cur.sha, msg);
        }
        GitHub.FileData now = gh.get("control.json");
        if (now != null) sha = now.sha;
    }

    static JSONObject obj(JSONObject parent, String key) {
        JSONObject o = parent.optJSONObject(key);
        if (o == null) { o = new JSONObject(); try { parent.put(key, o); } catch (Exception ignored) {} }
        return o;
    }

    static JSONArray arr(JSONObject parent, String key) {
        JSONArray a = parent.optJSONArray(key);
        if (a == null) { a = new JSONArray(); try { parent.put(key, a); } catch (Exception ignored) {} }
        return a;
    }
}
