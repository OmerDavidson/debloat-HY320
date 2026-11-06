package com.vibehome.launcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.util.Base64;

/** A connected, authorized ADB shell can read a draft. Nobody can apply or inject changes here. */
public final class ProjectionDraftProvider extends ContentProvider {
    private void checkCaller() {
        int caller = Binder.getCallingUid();
        if (caller != Process.myUid() && caller != 2000) throw new SecurityException("Owner app or ADB shell only");
    }
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String argument, Bundle extras) {
        checkCaller();
        if (!"read_draft".equals(method)) throw new IllegalArgumentException("Read-only endpoint");
        String draft = getContext().getSharedPreferences("projection_drafts", 0).getString("pending", "{}");
        Bundle result = new Bundle();
        result.putString("draft_base64", Base64.encodeToString(draft.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP));
        return result;
    }
    @Override public Cursor query(Uri uri, String[] columns, String where, String[] args, String order) { checkCaller(); return null; }
    @Override public String getType(Uri uri) { checkCaller(); return "application/json"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new SecurityException("Read-only"); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new SecurityException("Read-only"); }
}
