package com.sbwi.autoupload;

import android.content.ContentResolver;
import android.net.Uri;

import org.json.JSONObject;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** GitHub REST helper: reads/writes the control files and hosts phone uploads temporarily. */
public class GitHub {
    private final String token;
    public final String repo; // owner/name

    public GitHub(String token, String repo) {
        this.token = token == null ? "" : token.trim();
        this.repo = (repo == null || repo.trim().isEmpty()) ? Prefs.DEFAULT_REPO : repo.trim();
    }

    public static class Resp { int code; String body; }

    private HttpURLConnection open(String method, String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com" + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(300000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "UploadControl");
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    public Resp api(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(method, path);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        r.body = Mcp.readAll(r.code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (r.code == 401) throw new Exception("GitHub token rejected. Check it in Setup.");
        return r;
    }

    private String fail(Resp r) {
        try { return "GitHub " + r.code + ": " + new JSONObject(r.body).optString("message"); }
        catch (Exception e) { return "GitHub " + r.code; }
    }

    public static class FileData { public String text; public String sha; }

    /** Returns null if the file does not exist. */
    public FileData get(String path) throws Exception {
        Resp r = api("GET", "/repos/" + repo + "/contents/" + path + "?ref=main", null);
        if (r.code == 404) return null;
        if (r.code >= 400) throw new Exception(fail(r));
        JSONObject o = new JSONObject(r.body);
        FileData f = new FileData();
        f.sha = o.optString("sha");
        String b64 = o.optString("content").replace("\n", "");
        if (b64.isEmpty() && o.optLong("size") > 0) {
            // big file: use raw media type
            HttpURLConnection c = open("GET", "/repos/" + repo + "/contents/" + path + "?ref=main");
            c.setRequestProperty("Accept", "application/vnd.github.raw+json");
            f.text = Mcp.readAll(c.getInputStream());
        } else {
            f.text = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        }
        return f;
    }

    public void put(String path, String text, String sha, String message) throws Exception {
        JSONObject b = new JSONObject();
        b.put("message", message);
        b.put("content", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        b.put("branch", "main");
        if (sha != null) b.put("sha", sha);
        Resp r = api("PUT", "/repos/" + repo + "/contents/" + path, b);
        if (r.code >= 400) throw new Exception(fail(r));
    }

    /** Checks the token can push to the repo. Returns a short status line. */
    public String test() throws Exception {
        Resp r = api("GET", "/repos/" + repo, null);
        if (r.code == 404) throw new Exception("Repo " + repo + " not found (or the token can't see it)");
        if (r.code >= 400) throw new Exception(fail(r));
        JSONObject o = new JSONObject(r.body);
        JSONObject perm = o.optJSONObject("permissions");
        boolean push = perm != null && perm.optBoolean("push");
        if (!o.optBoolean("private") && perm == null) push = false;
        return o.optString("full_name") + (push ? " — can write ✔" : " — read only ✖ (token needs Contents: write)");
    }

    public interface Progress { void on(long sent, long total); }

    /**
     * Pushes a video from the phone to a temporary branch and returns its public raw URL.
     * The caller deletes the branch with deleteBranch() once iHatePosting has fetched it.
     */
    public String[] hostVideo(ContentResolver cr, Uri uri, String fileName, long size, Progress p) throws Exception {
        Resp ref = api("GET", "/repos/" + repo + "/git/ref/heads/main", null);
        if (ref.code >= 400) throw new Exception(fail(ref));
        String sha = new JSONObject(ref.body).getJSONObject("object").getString("sha");
        String branch = "tmp-phone-" + System.currentTimeMillis();
        JSONObject nb = new JSONObject();
        nb.put("ref", "refs/heads/" + branch);
        nb.put("sha", sha);
        Resp cr1 = api("POST", "/repos/" + repo + "/git/refs", nb);
        if (cr1.code >= 400) throw new Exception(fail(cr1));

        String path = "phone/" + fileName;
        HttpURLConnection c = open("PUT", "/repos/" + repo + "/contents/" + path);
        c.setDoOutput(true);
        c.setChunkedStreamingMode(64 * 1024);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream raw = c.getOutputStream();
        raw.write(("{\"message\":\"temp phone upload\",\"branch\":\"" + branch + "\",\"content\":\"").getBytes(StandardCharsets.UTF_8));
        OutputStream keepOpen = new FilterOutputStream(raw) {
            @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
            @Override public void close() throws IOException { out.flush(); }
        };
        OutputStream enc = Base64.getEncoder().wrap(keepOpen);
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new Exception("Can't open the video");
        byte[] buf = new byte[48 * 1024];
        long sent = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            enc.write(buf, 0, n);
            sent += n;
            if (p != null) p.on(sent, size);
        }
        in.close();
        enc.close();
        raw.write("\"}".getBytes(StandardCharsets.UTF_8));
        raw.close();
        int code = c.getResponseCode();
        String body = Mcp.readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) {
            deleteBranch(branch);
            Resp rr = new Resp(); rr.code = code; rr.body = body;
            throw new Exception(fail(rr));
        }
        String url = "https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + path.replace(" ", "%20");
        return new String[]{url, branch};
    }

    public void deleteBranch(String branch) {
        try { api("DELETE", "/repos/" + repo + "/git/refs/heads/" + URLEncoder.encode(branch, "UTF-8"), null); }
        catch (Exception ignored) {}
    }
}
