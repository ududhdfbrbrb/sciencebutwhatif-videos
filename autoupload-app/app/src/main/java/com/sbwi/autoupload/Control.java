package com.sbwi.autoupload;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The auto-upload settings. They live in a DRAFT post in iHatePosting whose text starts with
 * "UPLOAD CONTROL SETTINGS" — both hourly tasks read it at the start of every run.
 * A draft never publishes, so nothing reaches TikTok.
 */
public class Control {
    public static final String MARK = "UPLOAD CONTROL SETTINGS";
    public JSONObject json;
    String postId;
    JSONArray accountIds;

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

    static JSONObject parse(String text) {
        try {
            int i = text.indexOf('{');
            if (i < 0) return null;
            return new JSONObject(text.substring(i));
        } catch (Exception e) { return null; }
    }

    public static Control load(Mcp m) throws Exception {
        Control c = new Control();
        c.json = defaults();
        String cursor = null;
        for (int page = 0; page < 5; page++) {
            JSONObject r = m.call("list_posts", Mcp.args("status", "draft", "limit", 100, "cursor", cursor));
            JSONArray a = r.optJSONArray("posts");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject p = a.getJSONObject(i);
                String text = p.optString("baseContent");
                if (!text.startsWith(MARK)) continue;
                c.postId = p.optString("id");
                c.accountIds = new JSONArray();
                JSONArray tg = p.optJSONArray("targets");
                if (tg != null) for (int k = 0; k < tg.length(); k++) {
                    String id = tg.getJSONObject(k).optString("socialAccountId", tg.getJSONObject(k).optString("accountId", ""));
                    if (!id.isEmpty()) c.accountIds.put(id);
                }
                JSONObject cur = parse(text);
                if (cur != null) {
                    java.util.Iterator<String> it = cur.keys();
                    while (it.hasNext()) { String k = it.next(); c.json.put(k, cur.get(k)); }
                }
                return c;
            }
            if (!r.optBoolean("hasMore")) break;
            cursor = r.optString("nextCursor");
        }
        return c;
    }

    String text() throws Exception {
        return MARK + " (draft, never posted; edited by the Upload Control app)\n" + json.toString();
    }

    public void save(Mcp m, String account) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        json.put("updated", f.format(new Date()) + " (phone)");
        if (postId == null) {
            JSONObject r = m.call("create_post", Mcp.args("text", text(), "platforms", new JSONArray().put(account), "action", "draft"));
            JSONObject p = r.optJSONObject("post");
            if (p != null) postId = p.optString("id");
            return;
        }
        if (accountIds == null || accountIds.length() == 0) accountIds = new JSONArray().put(account);
        m.call("update_post", Mcp.args("postId", postId, "baseContent", text(), "accountIds", accountIds, "action", "draft"));
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
