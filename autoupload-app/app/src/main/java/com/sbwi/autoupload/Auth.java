package com.sbwi.autoupload;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** "Sign in with iHatePosting" — OAuth 2.1 + PKCE, no keys to type. */
public class Auth {
    static final String BASE = "https://ihateposting.com/oauth";
    static final String RESOURCE = "https://ihateposting.com/mcp/oauth";
    static final String REDIRECT = "com.sbwi.autoupload://oauth";

    static String rnd(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static String challenge(String verifier) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.encodeToString(d, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    static String post(String url, String body, String type) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Content-Type", type);
        c.setRequestProperty("Accept", "application/json");
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = c.getResponseCode();
        String txt = Mcp.readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) {
            String msg = txt;
            try { JSONObject o = new JSONObject(txt); msg = o.optString("error_description", o.optString("error", txt)); } catch (Exception ignored) {}
            throw new Exception("Sign-in error (" + code + "): " + Mcp.cut(msg));
        }
        return txt;
    }

    /** Step 1: register this phone as a client (once) and build the browser URL. */
    static Uri start(Prefs p) throws Exception {
        String client = p.get("oauthClient");
        if (client.isEmpty()) {
            JSONObject reg = new JSONObject();
            reg.put("client_name", "Upload Control (Android)");
            reg.put("redirect_uris", new JSONArray().put(REDIRECT));
            reg.put("grant_types", new JSONArray().put("authorization_code").put("refresh_token"));
            reg.put("response_types", new JSONArray().put("code"));
            reg.put("token_endpoint_auth_method", "none");
            reg.put("scope", "mcp offline_access");
            JSONObject r = new JSONObject(post(BASE + "/register", reg.toString(), "application/json"));
            client = r.getString("client_id");
            p.set("oauthClient", client);
        }
        String verifier = rnd(48);
        String state = rnd(16);
        p.set("oauthVerifier", verifier);
        p.set("oauthState", state);
        return Uri.parse(BASE + "/authorize?response_type=code"
                + "&client_id=" + enc(client)
                + "&redirect_uri=" + enc(REDIRECT)
                + "&scope=" + enc("mcp offline_access")
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge(verifier))
                + "&code_challenge_method=S256"
                + "&resource=" + enc(RESOURCE));
    }

    /** Step 2: the browser came back with ?code=… */
    static void finish(Prefs p, Uri back) throws Exception {
        String err = back.getQueryParameter("error");
        if (err != null) throw new Exception("Sign-in cancelled: " + err);
        String code = back.getQueryParameter("code");
        String state = back.getQueryParameter("state");
        if (code == null) throw new Exception("No sign-in code came back");
        if (state != null && !state.equals(p.get("oauthState"))) throw new Exception("Sign-in expired, try again");
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(REDIRECT)
                + "&client_id=" + enc(p.get("oauthClient"))
                + "&code_verifier=" + enc(p.get("oauthVerifier"))
                + "&resource=" + enc(RESOURCE);
        save(p, new JSONObject(post(BASE + "/token", body, "application/x-www-form-urlencoded")));
    }

    static void save(Prefs p, JSONObject t) throws Exception {
        p.set("access", t.getString("access_token"));
        String rt = t.optString("refresh_token", "");
        if (!rt.isEmpty()) p.set("refresh", rt);
        p.setLong("accessExp", System.currentTimeMillis() + t.optLong("expires_in", 3600) * 1000L - 60_000L);
    }

    /** Returns true if a new access token was obtained. */
    static synchronized boolean refresh(Prefs p) {
        try {
            String rt = p.get("refresh");
            if (rt.isEmpty()) return false;
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + enc(rt)
                    + "&client_id=" + enc(p.get("oauthClient"))
                    + "&resource=" + enc(RESOURCE);
            save(p, new JSONObject(post(BASE + "/token", body, "application/x-www-form-urlencoded")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
