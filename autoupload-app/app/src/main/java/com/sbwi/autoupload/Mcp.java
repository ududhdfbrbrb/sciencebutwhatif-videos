package com.sbwi.autoupload;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny MCP (Streamable HTTP) client for the iHatePosting server. */
public class Mcp {
    static final String KEY_ENDPOINT = "https://ihateposting.com/mcp?client=uploadcontrol";
    static final String OAUTH_ENDPOINT = "https://ihateposting.com/mcp/oauth";
    private final Prefs prefs;
    private String session;
    private int nextId = 1;
    private boolean ready;
    private boolean retried401;

    public Mcp(Prefs p) { this.prefs = p; }

    private boolean oauth() { return prefs.signedIn(); }

    private String bearer() {
        if (oauth()) {
            if (System.currentTimeMillis() > prefs.getLong("accessExp")) Auth.refresh(prefs);
            return prefs.get("access");
        }
        return prefs.ihpKey().trim();
    }

    static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = is.read(b)) > 0) bo.write(b, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }

    private JSONObject rpc(String method, JSONObject params, boolean notification) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(oauth() ? OAUTH_ENDPOINT : KEY_ENDPOINT).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(180000);
        c.setRequestProperty("Authorization", "Bearer " + bearer());
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json, text/event-stream");
        c.setRequestProperty("MCP-Protocol-Version", "2025-03-26");
        if (session != null) c.setRequestProperty("Mcp-Session-Id", session);
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        if (params != null) body.put("params", params);
        int myId = nextId++;
        if (!notification) body.put("id", myId);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = c.getResponseCode();
        String sid = c.getHeaderField("Mcp-Session-Id");
        if (sid != null) session = sid;
        String txt = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code == 401 && oauth() && !retried401) { retried401 = true; if (Auth.refresh(prefs)) { ready = false; session = null; throw new SessionGone(); } }
        if (code == 401 || code == 403) throw new Exception(oauth() ? "Signed out of iHatePosting. Sign in again in Setup." : "iHatePosting API key rejected (" + code + "). Check it in Setup.");
        if (code == 404 && session != null) { session = null; ready = false; throw new SessionGone(); }
        if (notification) return null;
        if (code >= 400) throw new Exception("iHatePosting HTTP " + code + ": " + cut(txt));
        JSONObject resp = null;
        String ct = c.getContentType();
        if ((ct != null && ct.contains("event-stream")) || txt.startsWith("event:") || txt.startsWith("data:")) {
            for (String line : txt.split("\n")) {
                line = line.trim();
                if (!line.startsWith("data:")) continue;
                String d = line.substring(5).trim();
                if (!d.startsWith("{")) continue;
                JSONObject o = new JSONObject(d);
                if (o.optInt("id", -1) == myId) { resp = o; break; }
            }
        } else if (txt.trim().startsWith("{")) {
            resp = new JSONObject(txt);
        }
        if (resp == null) throw new Exception("Empty answer from iHatePosting");
        if (resp.has("error")) throw new Exception(resp.getJSONObject("error").optString("message", "error"));
        return resp.optJSONObject("result");
    }

    static class SessionGone extends Exception {}

    static String cut(String s) { return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s); }

    private synchronized void init() throws Exception {
        if (ready) return;
        JSONObject p = new JSONObject();
        p.put("protocolVersion", "2025-03-26");
        p.put("capabilities", new JSONObject());
        JSONObject ci = new JSONObject();
        ci.put("name", "upload-control-android");
        ci.put("version", "1.0");
        p.put("clientInfo", ci);
        rpc("initialize", p, false);
        try { rpc("notifications/initialized", null, true); } catch (Exception ignored) {}
        ready = true;
    }

    /** Calls a tool and returns its JSON result. */
    public synchronized JSONObject call(String tool, JSONObject args) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                init();
                JSONObject p = new JSONObject();
                p.put("name", tool);
                p.put("arguments", args == null ? new JSONObject() : args);
                JSONObject r = rpc("tools/call", p, false);
                if (r == null) throw new Exception("No result");
                String text = "";
                JSONArray content = r.optJSONArray("content");
                if (content != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject ci = content.optJSONObject(i);
                        if (ci != null && "text".equals(ci.optString("type"))) sb.append(ci.optString("text"));
                    }
                    text = sb.toString();
                }
                if (r.optBoolean("isError")) throw new Exception(cut(text.isEmpty() ? r.toString() : text));
                JSONObject sc = r.optJSONObject("structuredContent");
                if (sc != null) return sc;
                String t = text.trim();
                if (t.startsWith("{")) return new JSONObject(t);
                JSONObject o = new JSONObject();
                o.put("text", text);
                return o;
            } catch (SessionGone g) {
                // retry once with a fresh session
            }
        }
        throw new Exception("iHatePosting session expired, try again");
    }

    public static JSONObject args(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null) o.put((String) kv[i], kv[i + 1]);
        } catch (Exception ignored) {}
        return o;
    }
}
