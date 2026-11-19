package com.sbwi.autoupload;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    public static final String DEFAULT_REPO = "ududhdfbrbrb/sciencebutwhatif-videos";
    public static final String DEFAULT_ACCOUNT = "cmwfkrw390cleqk0xbzaoraei";

    private final SharedPreferences sp;
    public Prefs(Context c) { sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }

    public String ihpKey() { return sp.getString("ihp", ""); }
    public String ghToken() { return sp.getString("gh", ""); }
    public String repo() { return sp.getString("repo", DEFAULT_REPO); }
    public String account() { return sp.getString("acct", DEFAULT_ACCOUNT); }
    public String accountName() { return sp.getString("acctName", "@sciencebutwhatif"); }
    public boolean watch() { return sp.getBoolean("watch", true); }
    public String seenFailed() { return sp.getString("seenFailed", ""); }
    public long lastStuckAlert() { return sp.getLong("stuck", 0); }

    public void set(String k, String v) { sp.edit().putString(k, v).apply(); }
    public void setBool(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public void setLong(String k, long v) { sp.edit().putLong(k, v).apply(); }

    public boolean ready() { return !ihpKey().isEmpty(); }
    public Mcp mcp() { return new Mcp(ihpKey()); }
    public GitHub gh() { return new GitHub(ghToken(), repo()); }
}
