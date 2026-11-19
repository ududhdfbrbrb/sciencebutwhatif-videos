package com.sbwi.autoupload;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ---- colors ----
    static final int BG = Color.rgb(7, 10, 26);
    static final int CARD = Color.argb(235, 20, 22, 48);
    static final int STROKE = Color.argb(70, 140, 150, 255);
    static final int WHITE = Color.WHITE;
    static final int GREY = Color.rgb(160, 165, 190);
    static final int CYAN = Color.rgb(90, 210, 255);
    static final int YELLOW = Color.rgb(255, 214, 64);
    static final int RED = Color.rgb(255, 82, 82);
    static final int GREEN = Color.rgb(80, 220, 140);
    static final int ORANGE = Color.rgb(255, 150, 60);
    static final int PURPLE = Color.rgb(150, 110, 255);

    static final String[] FORMATS = {"shape", "capital", "landmark", "food", "facts", "animals", "ranking2"};
    static final String[] FORMAT_NAMES = {"Guess by shape", "Capital city", "Landmarks", "Food", "Crazy facts", "National animals", "Second ranking video"};

    final ExecutorService pool = Executors.newFixedThreadPool(3);
    final Handler ui = new Handler(Looper.getMainLooper());
    Prefs prefs;
    LinearLayout content;
    LinearLayout tabBar;
    String tab = "home";
    static final int PICK_VIDEO = 7;
    Uri pickedVideo;
    String pickedName;
    long pickedSize;

    interface Job<T> { T run() throws Exception; }
    interface Done<T> { void ok(T t); }

    <T> void bg(Job<T> job, Done<T> done) {
        pool.execute(() -> {
            try {
                T r = job.run();
                ui.post(() -> { if (!isFinishing()) done.ok(r); });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                ui.post(() -> toast(msg));
            }
        });
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((th, ex) -> {
            java.io.StringWriter sw = new java.io.StringWriter();
            ex.printStackTrace(new java.io.PrintWriter(sw));
            getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("crash", sw.toString()).commit();
            if (prev != null) prev.uncaughtException(th, ex);
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(6));
        TextView title = t("Upload Control", 24, WHITE, true);
        header.addView(title);
        header.addView(t(prefs.accountName() + " auto upload", 13, GREY, false));
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(6), dp(14), dp(24));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(Color.rgb(12, 14, 34));
        tabBar.setPadding(dp(4), dp(6), dp(4), dp(10));
        root.addView(tabBar);
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        try { WatchJob.schedule(this); } catch (Exception ignored) {}

        Intent in = getIntent();
        if (in != null && Intent.ACTION_SEND.equals(in.getAction())) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) { setPicked(u); tab = "upload"; }
        }
        if (!prefs.ready()) tab = "setup";
        if (in != null && in.getData() != null && "com.sbwi.autoupload".equals(in.getData().getScheme())) { handleSignIn(in.getData()); }
        else show(tab);
        if (!prefs.ready() && prefs.get("autoSignin").isEmpty()) { prefs.set("autoSignin", "1"); signIn(); }
        String crash = getSharedPreferences("cfg", MODE_PRIVATE).getString("crash", "");
        if (!crash.isEmpty()) {
            getSharedPreferences("cfg", MODE_PRIVATE).edit().remove("crash").apply();
            TextView tv = t(crash.length() > 3000 ? crash.substring(0, 3000) : crash, 11, GREY, false);
            tv.setTextIsSelectable(true);
            tv.setPadding(dp(16), dp(8), dp(16), dp(8));
            ScrollView s2 = new ScrollView(this);
            s2.addView(tv);
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("The app crashed last time — screenshot this").setView(s2)
                    .setPositiveButton("OK", null).show();
        }
    }

    @Override protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        setIntent(in);
        if (in.getData() != null && "com.sbwi.autoupload".equals(in.getData().getScheme())) handleSignIn(in.getData());
        else if (Intent.ACTION_SEND.equals(in.getAction())) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) { setPicked(u); show("upload"); }
        }
    }

    void signIn() {
        bg(() -> Auth.start(prefs), uri -> startActivity(new Intent(Intent.ACTION_VIEW, uri)));
    }

    void handleSignIn(Uri back) {
        show("setup");
        bg(() -> {
            Auth.finish(prefs, back);
            JSONObject r = prefs.mcp().call("list_accounts", null);
            JSONArray arr = r.optJSONArray("accounts");
            // keep @sciencebutwhatif if it is there, else take the first TikTok account
            boolean found = false;
            String firstTiktok = null, firstName = null;
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.getJSONObject(i);
                if (x.optString("id").equals(prefs.account())) found = true;
                if (firstTiktok == null && "tiktok".equals(x.optString("platform"))) { firstTiktok = x.optString("id"); firstName = "@" + x.optString("handle"); }
            }
            if (!found && firstTiktok != null) { prefs.set("acct", firstTiktok); prefs.set("acctName", firstName); }
            return r;
        }, r -> {
            JSONObject acc = r.optJSONObject("account");
            toast("Signed in" + (acc == null ? "" : " as " + acc.optString("email")) + " ✔");
            show("home");
        });
    }

    // ---------------------------------------------------------------- UI helpers
    TextView t(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, STROKE, 18));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    LinearLayout section(String title) {
        LinearLayout c = card();
        c.addView(t(title, 16, WHITE, true));
        content.addView(c);
        return c;
    }

    Button btn(String label, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(8, 10, 24));
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(color, 0, 14));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setMinHeight(dp(42));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    Button wideBtn(String label, int color, View.OnClickListener l) {
        Button b = btn(label, color, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    HorizontalScrollView hscroll(View v) {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(v);
        return h;
    }

    EditText input(String hint, String value, boolean multi, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(100, 105, 130));
        e.setTextColor(WHITE);
        e.setTextSize(15);
        e.setText(value == null ? "" : value);
        e.setBackground(round(Color.rgb(12, 14, 34), STROKE, 12));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else if (multi) { e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); e.setMinLines(3); e.setGravity(Gravity.TOP); }
        else e.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        e.setLayoutParams(lp);
        return e;
    }

    Switch sw(String label, boolean on) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(WHITE);
        s.setTextSize(15);
        s.setChecked(on);
        s.setPadding(0, dp(10), 0, dp(4));
        return s;
    }

    TextView pill(String s, int color) {
        TextView v = t(s, 12, color, true);
        v.setBackground(round(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)), color, 10));
        v.setPadding(dp(8), dp(2), dp(8), dp(2));
        return v;
    }

    TextView loading(LinearLayout into) {
        TextView v = t("Loading…", 14, GREY, false);
        v.setPadding(0, dp(8), 0, 0);
        into.addView(v);
        return v;
    }

    void buildTabs() {
        tabBar.removeAllViews();
        String[][] tabs = {{"home", "⌂", "Home"}, {"posts", "☰", "Posts"}, {"upload", "⇪", "Upload"}, {"auto", "⚙", "Auto"}, {"setup", "🔑", "Setup"}};
        for (String[] tb : tabs) {
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            boolean sel = tb[0].equals(tab);
            TextView ic = t(tb[1], 20, sel ? CYAN : GREY, false);
            ic.setGravity(Gravity.CENTER);
            TextView lb = t(tb[2], 11, sel ? CYAN : GREY, sel);
            lb.setGravity(Gravity.CENTER);
            b.addView(ic);
            b.addView(lb);
            b.setPadding(0, dp(4), 0, dp(2));
            if (sel) b.setBackground(round(Color.argb(30, 90, 210, 255), 0, 14));
            b.setOnClickListener(v -> show(tb[0]));
            tabBar.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    void show(String which) {
        tab = which;
        buildTabs();
        content.removeAllViews();
        if (!prefs.ready() && !which.equals("setup")) {
            LinearLayout c = section("Set up first");
            c.addView(t("Add your iHatePosting API key in Setup to start.", 14, GREY, false));
            c.addView(btn("Open Setup", CYAN, v -> show("setup")));
            return;
        }
        switch (which) {
            case "home": home(); break;
            case "posts": posts("scheduled"); break;
            case "upload": upload(); break;
            case "auto": auto(); break;
            default: setup();
        }
    }

    // ---------------------------------------------------------------- time helpers
    static String isoUtc(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    static String localOf(JSONObject post) {
        JSONObject l = post.optJSONObject("scheduledLocal");
        if (l == null) return post.optString("scheduledAt", "no time");
        return l.optString("date") + "  " + l.optString("time");
    }

    static int statusColor(String s) {
        switch (s) {
            case "published": return GREEN;
            case "scheduled": return CYAN;
            case "failed": return RED;
            case "publishing": return YELLOW;
            case "partial": return ORANGE;
            default: return GREY;
        }
    }

    // ---------------------------------------------------------------- HOME
    void home() {
        LinearLayout auto = section("Auto upload");
        TextView autoState = loading(auto);
        LinearLayout today = section("Last 24 hours");
        TextView todayState = loading(today);
        LinearLayout next = section("Coming up");
        TextView nextState = loading(next);
        LinearLayout runs = section("Recent runs");
        TextView runsState = loading(runs);
        LinearLayout stats = section("Stats (7 days)");
        TextView statsState = loading(stats);

        {
            bg(() -> Control.load(prefs.mcp()), ctl -> {
                auto.removeView(autoState);
                JSONObject c = ctl.json;
                boolean paused = c.optBoolean("paused");
                LinearLayout r = row();
                r.addView(pill(paused ? "PAUSED" : "RUNNING", paused ? RED : GREEN));
                auto.addView(r);
                JSONObject w = c.optJSONObject("whatif"), co = c.optJSONObject("country");
                auto.addView(t("What if videos (even hours): " + (w != null && w.optBoolean("enabled", true) ? "on, " + w.optInt("per_slot", 2) + " per slot" : "off"), 14, GREY, false));
                auto.addView(t("Country videos (odd hours): " + (co != null && co.optBoolean("enabled", true) ? "on, " + co.optInt("per_slot", 2) + " per slot" : "off"), 14, GREY, false));
                LinearLayout br = row();
                br.addView(btn(paused ? "Resume" : "Pause all", paused ? GREEN : RED, v -> {
                    try { c.put("paused", !paused); } catch (Exception ignored) {}
                    bg(() -> { ctl.save(prefs.mcp(), prefs.account()); return 1; },
                            x -> { toast(paused ? "Resumed — next run picks it up" : "Paused — next runs will skip"); show("home"); });
                }));
                br.addView(btn("Settings", CYAN, v -> show("auto")));
                auto.addView(br);
            });
            bg(() -> { GitHub.FileData f = prefs.gh().get("runlog.txt"); return f == null ? "" : f.text; }, log -> {
                runs.removeView(runsState);
                String[] lines = log.trim().isEmpty() ? new String[0] : log.trim().split("\n");
                if (lines.length == 0) runs.addView(t("No runs logged yet. Each run of the two tasks adds a line here.", 14, GREY, false));
                for (int i = lines.length - 1, n = 0; i >= 0 && n < 10; i--, n++) {
                    TextView tv = t(lines[i], 13, lines[i].contains("FAIL") ? RED : lines[i].contains("skip") ? YELLOW : GREY, false);
                    tv.setPadding(0, dp(4), 0, 0);
                    runs.addView(tv);
                }
            });
        }

        long now = System.currentTimeMillis();
        bg(() -> prefs.mcp().call("list_posts", Mcp.args("from", isoUtc(now - 24 * 3600_000L), "to", isoUtc(now), "limit", 200)), r -> {
            today.removeView(todayState);
            JSONArray a = r.optJSONArray("posts");
            int pub = 0, fail = 0, other = 0;
            if (a != null) for (int i = 0; i < a.length(); i++) {
                String s = a.optJSONObject(i).optString("status");
                if (s.equals("published")) pub++; else if (s.equals("failed")) fail++; else other++;
            }
            LinearLayout rr = row();
            rr.addView(bigStat(String.valueOf(pub), "posted", GREEN));
            rr.addView(bigStat(String.valueOf(fail), "failed", fail > 0 ? RED : GREY));
            rr.addView(bigStat((pub + fail + other) + "/15", "TikTok limit", YELLOW));
            today.addView(rr);
            if (fail > 0) today.addView(btn("See failed posts", RED, v -> posts("failed")));
        });

        bg(() -> prefs.mcp().call("list_posts", Mcp.args("status", "scheduled", "from", isoUtc(now), "limit", 10)), r -> {
            next.removeView(nextState);
            JSONArray a = r.optJSONArray("posts");
            if (a == null || a.length() == 0) {
                next.addView(t("Nothing scheduled. If the auto upload is running, the next run should add posts within the hour.", 14, GREY, false));
                return;
            }
            for (int i = 0; i < Math.min(4, a.length()); i++) {
                JSONObject p = a.optJSONObject(i);
                next.addView(t(localOf(p), 13, CYAN, true));
                TextView cap = t(p.optString("baseContent"), 14, WHITE, false);
                cap.setMaxLines(2);
                cap.setPadding(0, 0, 0, dp(6));
                next.addView(cap);
            }
            next.addView(btn("All scheduled", CYAN, v -> posts("scheduled")));
        });

        bg(() -> prefs.mcp().call("get_analytics", Mcp.args("range", "7d")), r -> {
            stats.removeView(statsState);
            renderJson(stats, r, 0);
        });
    }

    LinearLayout bigStat(String num, String label, int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(6), dp(22), 0);
        l.addView(t(num, 26, color, true));
        l.addView(t(label, 12, GREY, false));
        return l;
    }

    /** Shows any JSON answer in a readable way (used for analytics). */
    void renderJson(LinearLayout into, Object o, int depth) {
        if (depth > 3) return;
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            Iterator<String> it = j.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = j.opt(k);
                if (k.equals("note") || k.equals("timezone") || k.endsWith("Id") || k.equals("id")) continue;
                String label = pretty(k);
                if (v instanceof JSONObject || v instanceof JSONArray) {
                    TextView h = t(label, 14 - Math.min(depth, 2), depth == 0 ? YELLOW : CYAN, true);
                    h.setPadding(dp(depth * 10), dp(8), 0, 0);
                    into.addView(h);
                    renderJson(into, v, depth + 1);
                } else {
                    String s = String.valueOf(v);
                    if (s.length() > 90) s = s.substring(0, 90) + "…";
                    TextView tv = t(label + ": " + s, 13, GREY, false);
                    tv.setPadding(dp(depth * 10), dp(2), 0, 0);
                    into.addView(tv);
                }
            }
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < Math.min(a.length(), 5); i++) {
                Object v = a.opt(i);
                if (v instanceof JSONObject || v instanceof JSONArray) {
                    TextView d = t("• #" + (i + 1), 12, PURPLE, true);
                    d.setPadding(dp(depth * 10), dp(4), 0, 0);
                    into.addView(d);
                    renderJson(into, v, depth + 1);
                } else {
                    TextView tv = t("• " + v, 13, GREY, false);
                    tv.setPadding(dp(depth * 10), dp(2), 0, 0);
                    into.addView(tv);
                }
            }
            if (a.length() == 0) { TextView tv = t("(none)", 13, GREY, false); tv.setPadding(dp(depth * 10), 0, 0, 0); into.addView(tv); }
        }
    }

    static String pretty(String k) {
        StringBuilder sb = new StringBuilder();
        for (char ch : k.toCharArray()) {
            if (Character.isUpperCase(ch)) sb.append(' ').append(Character.toLowerCase(ch));
            else if (ch == '_') sb.append(' ');
            else sb.append(ch);
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? k : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------- POSTS
    void posts(String filter) {
        tab = "posts";
        buildTabs();
        content.removeAllViews();
        LinearLayout chips = row();
        String[][] f = {{"scheduled", "Scheduled"}, {"published", "Posted"}, {"failed", "Failed"}, {"", "All"}};
        for (String[] x : f) {
            Button b = btn(x[1], x[0].equals(filter) ? CYAN : Color.rgb(60, 64, 100), v -> posts(x[0]));
            if (!x[0].equals(filter)) b.setTextColor(WHITE);
            chips.addView(b);
        }
        content.addView(hscroll(chips));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        loadPosts(list, filter, null);
    }

    void loadPosts(LinearLayout list, String filter, String cursor) {
        TextView l = loading(list);
        JSONObject a = Mcp.args("limit", 25, "cursor", cursor);
        try {
            if (!filter.isEmpty()) a.put("status", filter);
            if (filter.equals("scheduled")) a.put("from", isoUtc(System.currentTimeMillis() - 3600_000L));
        } catch (Exception ignored) {}
        bg(() -> prefs.mcp().call("list_posts", a), r -> {
            list.removeView(l);
            JSONArray arr = r.optJSONArray("posts");
            if (cursor == null) {
                TextView tot = t(r.optInt("total") + " post" + (r.optInt("total") == 1 ? "" : "s"), 13, GREY, false);
                tot.setPadding(dp(4), dp(8), 0, 0);
                list.addView(tot);
            }
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p.optString("baseContent").startsWith(Control.MARK)) continue;
                list.addView(postCard(p, filter));
            }
            if (r.optBoolean("hasMore")) {
                Button more = wideBtn("Load more", Color.rgb(60, 64, 100), null);
                more.setTextColor(WHITE);
                String nc = r.optString("nextCursor");
                more.setOnClickListener(v -> { list.removeView(more); loadPosts(list, filter, nc); });
                list.addView(more);
            }
        });
    }

    View postCard(JSONObject p, String filter) {
        LinearLayout c = card();
        String status = p.optString("status");
        LinearLayout top = row();
        top.addView(pill(status.toUpperCase(Locale.US), statusColor(status)));
        TextView when = t("   " + localOf(p), 13, GREY, false);
        top.addView(when);
        c.addView(top);
        TextView cap = t(p.optString("baseContent"), 14, WHITE, false);
        cap.setPadding(0, dp(8), 0, 0);
        c.addView(cap);
        String url = null, err = null;
        JSONArray tg = p.optJSONArray("targets");
        if (tg != null) for (int i = 0; i < tg.length(); i++) {
            JSONObject x = tg.optJSONObject(i);
            if (!x.optString("externalUrl").isEmpty() && !"null".equals(x.optString("externalUrl"))) url = x.optString("externalUrl");
            if (!x.isNull("error") && !x.optString("error").isEmpty()) err = x.optString("error");
        }
        if (err != null) { TextView e = t("Error: " + err, 13, RED, false); e.setPadding(0, dp(6), 0, 0); c.addView(e); }
        LinearLayout acts = row();
        String id = p.optString("id");
        if (url != null) {
            String u = url;
            acts.addView(btn("Open", GREEN, v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)))));
        }
        if (status.equals("failed") || status.equals("partial"))
            acts.addView(btn("Retry", YELLOW, v -> bg(() -> prefs.mcp().call("retry_post", Mcp.args("postId", id)),
                    r -> { toast("Retried: " + r.optInt("retried") + " send(s) queued again"); posts(filter); })));
        if (status.equals("scheduled")) {
            acts.addView(btn("Move", CYAN, v -> pickDateTime((d, tm) -> bg(() -> prefs.mcp().call("reschedule_post", Mcp.args("postId", id, "scheduledDate", d, "scheduledTime", tm)),
                    r -> { toast("Moved to " + d + " " + tm); posts(filter); }))));
            acts.addView(btn("Edit", PURPLE, v -> editCaption(id, filter)));
            acts.addView(btn("Post now", ORANGE, v -> confirm("Post this now?", "It goes live on TikTok right away.", () -> postNow(id, filter))));
        }
        acts.addView(btn("Delete", RED, v -> {
            boolean pub = status.equals("published");
            confirm(pub ? "Remove from list?" : "Delete this post?",
                    pub ? "It stays live on TikTok. This only removes iHatePosting's record." : "It will not go out.",
                    () -> bg(() -> prefs.mcp().call("delete_post", Mcp.args("postId", id, "force", pub ? Boolean.TRUE : null)),
                            r -> { toast("Deleted"); posts(filter); }));
        }));
        c.addView(hscroll(acts));
        return c;
    }

    void confirm(String title, String msg, Runnable yes) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title).setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> yes.run())
                .setNegativeButton("Cancel", null).show();
    }

    interface DT { void picked(String date, String time); }

    void pickDateTime(DT cb) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.HOUR_OF_DAY, 1);
        new DatePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert, (v, y, m, d) ->
                new TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert, (tv, h, min) ->
                        cb.picked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d), String.format(Locale.US, "%02d:%02d", h, min)),
                        c.get(Calendar.HOUR_OF_DAY), 0, true).show(),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    JSONArray accountIdsOf(JSONObject post) {
        JSONArray ids = new JSONArray();
        JSONArray tg = post.optJSONArray("targets");
        if (tg != null) for (int i = 0; i < tg.length(); i++) {
            JSONObject x = tg.optJSONObject(i);
            String a = x.optString("accountId", x.optString("socialAccountId", ""));
            if (!a.isEmpty()) ids.put(a);
        }
        JSONArray direct = post.optJSONArray("accountIds");
        if (ids.length() == 0 && direct != null) ids = direct;
        if (ids.length() == 0) ids.put(prefs.account());
        return ids;
    }

    JSONArray mediaIdsOf(JSONObject post) {
        JSONArray out = new JSONArray();
        JSONArray m = post.optJSONArray("media");
        if (m == null) m = post.optJSONArray("mediaIds");
        if (m != null) for (int i = 0; i < m.length(); i++) {
            Object o = m.opt(i);
            if (o instanceof JSONObject) {
                JSONObject j = (JSONObject) o;
                if (j.optJSONObject("media") != null) j = j.optJSONObject("media");
                String id = j.optString("mediaId", j.optString("id", ""));
                if (!id.isEmpty()) out.put(id);
            } else if (o != null) out.put(String.valueOf(o));
        }
        return out;
    }

    JSONObject optionsOf(JSONObject post) {
        try {
            JSONObject opts = post.optJSONObject("options");
            if (opts != null) return opts;
            JSONObject out = new JSONObject();
            JSONArray tg = post.optJSONArray("targets");
            if (tg != null) for (int i = 0; i < tg.length(); i++) {
                JSONObject x = tg.getJSONObject(i);
                JSONObject o = x.optJSONObject("options");
                if (o != null && o.length() > 0) out.put(x.optString("platform", "tiktok"), o);
            }
            return out.length() > 0 ? out : null;
        } catch (Exception e) { return null; }
    }

    void editCaption(String id, String filter) {
        bg(() -> prefs.mcp().call("get_post", Mcp.args("postId", id)), raw -> {
            JSONObject p = raw.optJSONObject("post") != null ? raw.optJSONObject("post") : raw;
            EditText e = input("Caption", p.optString("baseContent"), true, false);
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Edit caption").setView(e)
                    .setPositiveButton("Save", (d, w) -> bg(() -> {
                        JSONObject a = Mcp.args("postId", id, "baseContent", e.getText().toString(), "accountIds", accountIdsOf(p), "action", "schedule");
                        JSONObject loc = p.optJSONObject("scheduledLocal");
                        if (loc != null) { a.put("scheduledDate", loc.optString("date")); a.put("scheduledTime", loc.optString("time")); }
                        JSONArray media = mediaIdsOf(p);
                        if (media.length() > 0) a.put("mediaIds", media);
                        if (optionsOf(p) != null) a.put("options", optionsOf(p));
                        return prefs.mcp().call("update_post", a);
                    }, r -> { toast("Caption saved"); posts(filter); }))
                    .setNegativeButton("Cancel", null).show();
        });
    }

    void postNow(String id, String filter) {
        bg(() -> {
            JSONObject raw = prefs.mcp().call("get_post", Mcp.args("postId", id));
            JSONObject p = raw.optJSONObject("post") != null ? raw.optJSONObject("post") : raw;
            JSONObject a = Mcp.args("postId", id, "baseContent", p.optString("baseContent"), "accountIds", accountIdsOf(p), "action", "now");
            JSONArray media = mediaIdsOf(p);
            if (media.length() > 0) a.put("mediaIds", media);
            if (optionsOf(p) != null) a.put("options", optionsOf(p));
            return prefs.mcp().call("update_post", a);
        }, r -> { toast("Posting now"); posts(filter); });
    }

    // ---------------------------------------------------------------- UPLOAD
    void setPicked(Uri u) {
        pickedVideo = u;
        pickedName = "video.mp4";
        pickedSize = -1;
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0) pickedName = c.getString(ni);
                if (si >= 0) pickedSize = c.getLong(si);
            }
        } catch (Exception ignored) {}
        pickedName = pickedName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!pickedName.toLowerCase(Locale.US).endsWith(".mp4") && !pickedName.toLowerCase(Locale.US).endsWith(".mov")) pickedName += ".mp4";
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_VIDEO && res == RESULT_OK && data != null && data.getData() != null) {
            setPicked(data.getData());
            libraryMediaId = null;
            show("upload");
        }
    }

    void upload() {
        LinearLayout c = section("Post your own video");
        c.addView(t("Pick a video from your phone (or one already in your iHatePosting library), write a caption, then post it now or schedule it on " + prefs.accountName() + ".", 14, GREY, false));
        TextView file = t(pickedVideo == null ? "No video picked" : pickedName + (pickedSize > 0 ? String.format(Locale.US, "  (%.1f MB)", pickedSize / 1048576.0) : ""), 14, pickedVideo == null ? GREY : CYAN, true);
        file.setPadding(0, dp(10), 0, 0);
        c.addView(file);
        LinearLayout pr = row();
        pr.addView(btn(pickedVideo == null ? "Pick from phone" : "Pick another", CYAN, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("video/*");
            startActivityForResult(i, PICK_VIDEO);
        }));
        Button lib = btn("From library", Color.rgb(60, 64, 100), v -> pickFromLibrary(file));
        lib.setTextColor(WHITE);
        pr.addView(lib);
        c.addView(hscroll(pr));
        EditText cap = input("Caption (short, normal wording)", "", true, false);
        c.addView(cap);
        LinearLayout tags = row();
        for (String h : new String[]{"#whatif", "#science", "#geography", "#countries", "#quiz", "#facts", "#space", "#ranking"}) {
            Button b = btn(h, Color.rgb(60, 64, 100), v -> {
                String s = cap.getText().toString();
                if (!s.contains(h)) cap.setText((s.trim() + " " + h).trim());
                cap.setSelection(cap.getText().length());
            });
            b.setTextColor(WHITE);
            tags.addView(b);
        }
        c.addView(hscroll(tags));
        CheckBox ai = new CheckBox(this);
        ai.setText("Label as AI-generated content");
        ai.setTextColor(WHITE);
        ai.setChecked(true);
        c.addView(ai);
        CheckBox comments = new CheckBox(this);
        comments.setText("Allow comments, duets and stitches");
        comments.setTextColor(WHITE);
        comments.setChecked(true);
        c.addView(comments);
        TextView status = t("", 14, YELLOW, false);
        status.setPadding(0, dp(8), 0, 0);
        c.addView(status);

        LinearLayout r = row();
        r.addView(btn("Post now", GREEN, v -> doUpload(cap.getText().toString(), null, null, ai.isChecked(), comments.isChecked(), status)));
        r.addView(btn("Schedule…", CYAN, v -> pickDateTime((d, tm) -> doUpload(cap.getText().toString(), d, tm, ai.isChecked(), comments.isChecked(), status))));
        c.addView(r);
    }

    String libraryMediaId;

    void pickFromLibrary(TextView label) {
        bg(() -> prefs.mcp().call("list_media", Mcp.args("limit", 30)), r -> {
            JSONArray a = r.optJSONArray("media");
            if (a == null) a = r.optJSONArray("assets");
            if (a == null) a = r.optJSONArray("items");
            if (a == null || a.length() == 0) { toast("Library is empty"); return; }
            JSONArray vids = new JSONArray();
            for (int i = 0; i < a.length(); i++) { JSONObject m = a.optJSONObject(i); if (m != null && !"image".equals(m.optString("kind"))) vids.put(m); }
            String[] names = new String[vids.length()];
            for (int i = 0; i < vids.length(); i++) {
                JSONObject m = vids.optJSONObject(i);
                String nm = !m.isNull("filename") && m.has("filename") ? m.optString("filename") : !m.isNull("altText") && m.has("altText") ? m.optString("altText") : "Video " + (i + 1);
                if (nm.length() > 60) nm = nm.substring(0, 60) + "…";
                names[i] = nm + (m.has("durationSec") ? "  (" + Math.round(m.optDouble("durationSec")) + "s)" : "") + "  " + m.optString("createdAt", "").replace("T", " ").replaceAll("\\..*", "");
            }
            JSONArray vf = vids;
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Pick a video from iHatePosting")
                    .setItems(names, (d, w) -> {
                        libraryMediaId = vf.optJSONObject(w).optString("id");
                        pickedVideo = null;
                        label.setText("Library: " + names[w]);
                        label.setTextColor(CYAN);
                    }).show();
        });
    }

    /** Gets the picked video into iHatePosting and returns its media id. */
    String sendVideo(Uri u, String name, long size, TextView status) throws Exception {
        if (size > 0 && size <= 7L * 1048576) {
            ui.post(() -> status.setText("Uploading video…"));
            java.io.InputStream in = getContentResolver().openInputStream(u);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String b64 = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
            JSONObject media = prefs.mcp().call("upload_media", Mcp.args("base64", b64, "mime", "video/mp4", "filename", name));
            return mediaIdOf(media);
        }
        if (prefs.ghToken().isEmpty())
            throw new Exception("This video is over 7 MB. Add the optional GitHub token in Setup for big videos, or upload it on ihateposting.com and use \"From library\".");
        GitHub gh = prefs.gh();
        String[] hosted = gh.hostVideo(getContentResolver(), u, name, size, (sent, total) -> {
            if (total > 0) ui.post(() -> status.setText("Uploading video… " + (sent * 100 / total) + "%"));
        });
        try {
            ui.post(() -> status.setText("iHatePosting is fetching the video…"));
            return mediaIdOf(prefs.mcp().call("upload_media", Mcp.args("url", hosted[0], "filename", name)));
        } finally {
            gh.deleteBranch(hosted[1]);
        }
    }

    static String mediaIdOf(JSONObject media) throws Exception {
        String id = media.optString("id", media.optString("mediaId", ""));
        if (id.isEmpty() && media.optJSONObject("media") != null) id = media.optJSONObject("media").optString("id");
        if (id.isEmpty() && media.optJSONObject("asset") != null) id = media.optJSONObject("asset").optString("id");
        if (id.isEmpty()) throw new Exception("Upload gave no media id: " + Mcp.cut(media.toString()));
        return id;
    }

    void doUpload(String caption, String date, String time, boolean ai, boolean allow, TextView status) {
        if (pickedVideo == null && libraryMediaId == null) { toast("Pick a video first"); return; }
        if (caption.trim().isEmpty()) { toast("Write a caption"); return; }
        if (pickedSize > 95L * 1048576) { toast("Video is too big (max about 95 MB)"); return; }
        status.setText("Starting…");
        Uri u = pickedVideo;
        String lib = libraryMediaId;
        String name = System.currentTimeMillis() / 1000 + "_" + pickedName;
        long size = pickedSize;
        bg(() -> {
            {
                String mediaId = u != null ? sendVideo(u, name, size, status) : lib;
                ui.post(() -> status.setText(date == null ? "Posting…" : "Scheduling…"));
                JSONObject tk = new JSONObject();
                tk.put("tiktokPrivacy", "public");
                tk.put("tiktokAllowComment", allow);
                tk.put("tiktokAllowDuet", allow);
                tk.put("tiktokAllowStitch", allow);
                tk.put("tiktokAiGenerated", ai);
                JSONObject opts = new JSONObject();
                opts.put("tiktok", tk);
                JSONObject a = Mcp.args("text", caption.trim(), "platforms", new JSONArray().put(prefs.account()),
                        "mediaIds", new JSONArray().put(mediaId), "options", opts,
                        "action", date == null ? "now" : "schedule", "scheduledDate", date, "scheduledTime", time);
                return prefs.mcp().call("create_post", a);
            }
        }, r -> {
            status.setTextColor(GREEN);
            status.setText(date == null ? "Sent! It goes live in a minute or two." : "Scheduled for " + date + " " + time);
            pickedVideo = null;
            libraryMediaId = null;
            JSONArray w = r.optJSONArray("warnings");
            if (w != null && w.length() > 0) toast("Note: " + w.optString(0));
        });
    }

    // ---------------------------------------------------------------- AUTO (control.json)
    void auto() {
        TextView l = loading(content);
        bg(() -> Control.load(prefs.mcp()), ctl -> {
            content.removeView(l);
            buildAuto(ctl);
        });
    }

    void buildAuto(Control ctl) {
        JSONObject c = ctl.json;
        JSONObject w = Control.obj(c, "whatif"), co = Control.obj(c, "country"), q = Control.obj(c, "quiet_hours");

        LinearLayout main = section("Main switch");
        Switch run = sw("Auto upload running", !c.optBoolean("paused"));
        main.addView(run);
        main.addView(t("Off = both tasks still wake up every hour but make and post nothing.", 12, GREY, false));
        EditText limit = input("Daily post limit (max 15)", String.valueOf(c.optInt("daily_limit", 15)), false, false);
        limit.setInputType(InputType.TYPE_CLASS_NUMBER);
        main.addView(t("Max posts per 24 hours", 13, GREY, false));
        main.addView(limit);

        LinearLayout wi = section("What if videos  ·  even hours");
        Switch wOn = sw("Make what-if videos", w.optBoolean("enabled", true));
        wi.addView(wOn);
        Switch wTwo = sw("2 videos per slot (off = 1)", w.optInt("per_slot", 2) >= 2);
        wi.addView(wTwo);
        wi.addView(t("Topic queue — made first, in this order", 13, YELLOW, true));
        JSONArray wQueue = Control.arr(w, "queue");
        listEditor(wi, wQueue, "What if the Moon was made of cheese?");
        wi.addView(t("Never make these", 13, YELLOW, true));
        JSONArray wBlock = Control.arr(w, "blocked");
        listEditor(wi, wBlock, "topic or word to avoid");

        LinearLayout cs = section("Country videos  ·  odd hours");
        Switch cOn = sw("Make country videos", co.optBoolean("enabled", true));
        cs.addView(cOn);
        Switch cTwo = sw("2 videos per slot (off = 1)", co.optInt("per_slot", 2) >= 2);
        cs.addView(cTwo);
        cs.addView(t("The first video is always a ranking. Allowed formats for the second:", 13, GREY, false));
        JSONObject fm = Control.obj(co, "formats");
        CheckBox[] boxes = new CheckBox[FORMATS.length];
        for (int i = 0; i < FORMATS.length; i++) {
            CheckBox b = new CheckBox(this);
            b.setText(FORMAT_NAMES[i]);
            b.setTextColor(WHITE);
            b.setChecked(fm.optBoolean(FORMATS[i], true));
            boxes[i] = b;
            cs.addView(b);
        }
        cs.addView(t("Ranking topic queue", 13, YELLOW, true));
        JSONArray rQueue = Control.arr(co, "ranking_queue");
        listEditor(cs, rQueue, "Which country eats the most pizza?");
        cs.addView(t("Never use these", 13, YELLOW, true));
        JSONArray cBlock = Control.arr(co, "blocked");
        listEditor(cs, cBlock, "topic or country to avoid");

        LinearLayout qh = section("Quiet hours");
        Switch qOn = sw("Don't post during quiet hours", q.optBoolean("enabled", false));
        qh.addView(qOn);
        LinearLayout qr = row();
        EditText from = input("from", String.valueOf(q.optInt("from", 2)), false, false);
        EditText to = input("to", String.valueOf(q.optInt("to", 7)), false, false);
        from.setInputType(InputType.TYPE_CLASS_NUMBER);
        to.setInputType(InputType.TYPE_CLASS_NUMBER);
        qr.addView(t("From  ", 14, GREY, false));
        qr.addView(from, new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));
        qr.addView(t("  to  ", 14, GREY, false));
        qr.addView(to, new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));
        qr.addView(t("  o'clock", 14, GREY, false));
        qh.addView(qr);

        LinearLayout cap = section("Captions");
        cap.addView(t("Extra hashtags added to every caption", 13, GREY, false));
        EditText extra = input("#fyp #learn", c.optString("extra_hashtags"), false, false);
        cap.addView(extra);

        Button save = wideBtn("Save settings", GREEN, null);
        save.setOnClickListener(v -> {
            try {
                c.put("paused", !run.isChecked());
                int lim = parseInt(limit.getText().toString(), 15);
                c.put("daily_limit", Math.max(1, Math.min(15, lim)));
                w.put("enabled", wOn.isChecked());
                w.put("per_slot", wTwo.isChecked() ? 2 : 1);
                co.put("enabled", cOn.isChecked());
                co.put("per_slot", cTwo.isChecked() ? 2 : 1);
                for (int i = 0; i < FORMATS.length; i++) fm.put(FORMATS[i], boxes[i].isChecked());
                q.put("enabled", qOn.isChecked());
                q.put("from", Math.max(0, Math.min(23, parseInt(from.getText().toString(), 2))));
                q.put("to", Math.max(0, Math.min(23, parseInt(to.getText().toString(), 7))));
                String ex = extra.getText().toString().replace("#donutsmp", "").trim();
                c.put("extra_hashtags", ex);
            } catch (Exception e) { toast(e.getMessage()); return; }
            save.setEnabled(false);
            save.setText("Saving…");
            bg(() -> { ctl.save(prefs.mcp(), prefs.account()); return 1; },
                    x -> { toast("Saved — the next run uses it"); show("auto"); });
        });
        content.addView(save);

        LinearLayout used = section("Already made");
        TextView ul = loading(used);
        bg(() -> {
            GitHub gh = prefs.gh();
            GitHub.FileData a = gh.get("topics.txt"), b = gh.get("countryshapequiz/rankings.txt");
            return new String[]{a == null ? "" : a.text, b == null ? "" : b.text};
        }, r -> {
            used.removeView(ul);
            String[] wt = r[0].trim().isEmpty() ? new String[0] : r[0].trim().split("\n");
            String[] rk = r[1].trim().isEmpty() ? new String[0] : r[1].trim().split("\n");
            used.addView(t(wt.length + " what-if topics, " + rk.length + " ranking topics", 14, CYAN, true));
            used.addView(t("Latest what-if:", 13, YELLOW, true));
            for (int i = wt.length - 1; i >= Math.max(0, wt.length - 8); i--) used.addView(t("• " + wt[i], 13, GREY, false));
            used.addView(t("Latest rankings:", 13, YELLOW, true));
            for (int i = rk.length - 1; i >= Math.max(0, rk.length - 8); i--) used.addView(t("• " + rk[i], 13, GREY, false));
        });
    }

    static int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    /** Editable list of strings stored straight into the given JSONArray. */
    void listEditor(LinearLayout into, JSONArray arr, String hint) {
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        into.addView(items);
        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            items.removeAllViews();
            if (arr.length() == 0) items.addView(t("(empty)", 13, GREY, false));
            for (int i = 0; i < arr.length(); i++) {
                int idx = i;
                LinearLayout r = row();
                TextView tv = t((i + 1) + ". " + arr.optString(i), 14, WHITE, false);
                r.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView x = t("  ✕  ", 16, RED, true);
                x.setOnClickListener(v -> { arr.remove(idx); redraw[0].run(); });
                r.addView(x);
                r.setPadding(0, dp(4), 0, dp(4));
                items.addView(r);
            }
        };
        redraw[0].run();
        LinearLayout addRow = row();
        EditText e = input(hint, "", false, false);
        addRow.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = btn("Add", YELLOW, v -> {
            String s = e.getText().toString().trim();
            if (s.isEmpty()) return;
            arr.put(s);
            e.setText("");
            redraw[0].run();
        });
        ((LinearLayout.LayoutParams) add.getLayoutParams()).leftMargin = dp(8);
        addRow.addView(add);
        into.addView(addRow);
    }

    // ---------------------------------------------------------------- SETUP
    void setup() {
        LinearLayout a = section("iHatePosting");
        TextView st = t(prefs.signedIn() ? "Signed in ✔" : (prefs.ready() ? "Using an API key ✔" : "Not signed in"), 15, prefs.ready() ? GREEN : YELLOW, true);
        a.addView(st);
        a.addView(t("Sign in once in the browser — no keys to type. Posting goes to " + prefs.accountName() + ".", 13, GREY, false));
        LinearLayout ar = row();
        ar.addView(btn(prefs.signedIn() ? "Sign in again" : "Sign in with iHatePosting", CYAN, v -> signIn()));
        if (prefs.signedIn()) ar.addView(btn("Sign out", RED, v -> { prefs.signOut(); show("setup"); }));
        a.addView(hscroll(ar));
        LinearLayout accounts = new LinearLayout(this);
        accounts.setOrientation(LinearLayout.VERTICAL);
        a.addView(accounts);
        if (prefs.ready()) bg(() -> prefs.mcp().call("list_accounts", null), r -> {
            JSONObject acc = r.optJSONObject("account");
            if (acc != null) st.setText((prefs.signedIn() ? "Signed in ✔ " : "API key ✔ ") + acc.optString("email"));
            JSONArray arr = r.optJSONArray("accounts");
            if (arr == null || arr.length() < 2) return;
            accounts.addView(t("Post to which account?", 13, YELLOW, true));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.optJSONObject(i);
                String id = x.optString("id"), name = x.optString("platform") + " · " + x.optString("handle");
                boolean sel = id.equals(prefs.account());
                Button b = btn((sel ? "✔ " : "") + name, sel ? GREEN : Color.rgb(60, 64, 100), vv -> {
                    prefs.set("acct", id);
                    prefs.set("acctName", "@" + x.optString("handle"));
                    show("setup");
                });
                if (!sel) b.setTextColor(WHITE);
                accounts.addView(b);
            }
        });

        LinearLayout adv = section("Optional extras");
        adv.addView(t("Not needed for anything except posting phone videos bigger than 7 MB. Tap to show.", 13, GREY, false));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(View.GONE);
        Button showAdv = btn("Show", Color.rgb(60, 64, 100), v -> box.setVisibility(box.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        showAdv.setTextColor(WHITE);
        adv.addView(showAdv);
        adv.addView(box);
        box.addView(t("GitHub token (fine-grained, only the videos repo, Contents: read and write). Big videos are parked there for a minute, then deleted.", 13, GREY, false));
        EditText tok = input("github_pat_…", prefs.ghToken(), false, true);
        box.addView(tok);
        box.addView(btn("Create token on GitHub", Color.rgb(60, 64, 100), v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/settings/personal-access-tokens/new?name=Upload%20Control&description=Phone%20video%20uploads&target_name=" + prefs.repo().split("/")[0] + "&expires_in=none&contents=write")))));
        TextView gRes = t("", 13, GREY, false);
        box.addView(gRes);
        box.addView(btn("Save & test", CYAN, v -> {
            prefs.set("gh", tok.getText().toString().trim());
            gRes.setText("Testing…");
            bg(() -> prefs.gh().test(), s2 -> { gRes.setTextColor(s2.contains("✔") ? GREEN : YELLOW); gRes.setText(s2); });
        }));
        box.addView(t("iHatePosting API key (only if you don't want to sign in)", 13, GREY, false));
        EditText key = input("pk_live_…", prefs.ihpKey(), false, true);
        box.addView(key);
        box.addView(btn("Save key", CYAN, v -> { prefs.set("ihp", key.getText().toString().trim()); toast("Saved"); show("setup"); }));

        LinearLayout n = section("Alerts");
        Switch watch = sw("Notify me when a post fails or nothing is scheduled", prefs.watch());
        watch.setOnCheckedChangeListener((b, on) -> { prefs.setBool("watch", on); WatchJob.schedule(this); });
        n.addView(watch);
        n.addView(btn("Check now", CYAN, v -> bg(() -> { WatchJob.check(getApplicationContext()); return 1; }, x -> toast("Checked"))));

        LinearLayout h = section("How it works");
        TextView how = t("Two hourly tasks make the videos and schedule them on TikTok: what-if videos at even hours, country videos at odd hours. " +
                "Every run first reads your settings (kept as a hidden draft in iHatePosting that never posts) — this app edits them, so pausing, queues and limits take effect on the next run. " +
                "Posts themselves are managed live through iHatePosting.", 13, GREY, false);
        how.setMovementMethod(LinkMovementMethod.getInstance());
        h.addView(how);
    }
}
