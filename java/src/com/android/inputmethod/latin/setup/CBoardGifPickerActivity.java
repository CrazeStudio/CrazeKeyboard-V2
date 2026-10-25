/* Copyright (C) 2026 The CrazeStudio Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.inputmethod.latin.setup;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Uses Android's document picker and returns GIF content to the active IME when supported. */
public final class CBoardGifPickerActivity extends Activity {
    public static final String ACTION_GIF_SELECTED = "com.crazestudio.cboard.GIF_SELECTED";
    public static final String EXTRA_GIF_URI = "gif_uri";
    private static final int REQUEST_GIF = 4107;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/gif");
        try {
            startActivityForResult(pick, REQUEST_GIF);
        } catch (android.content.ActivityNotFoundException unavailable) {
            Intent allImages = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            allImages.addCategory(Intent.CATEGORY_OPENABLE);
            allImages.setType("image/*");
            try { startActivityForResult(allImages, REQUEST_GIF); }
            catch (android.content.ActivityNotFoundException noPicker) { finish(); }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_GIF && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try { getContentResolver().takePersistableUriPermission(uri, flags
                        & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) {}
                Intent result = new Intent(ACTION_GIF_SELECTED).setPackage(getPackageName());
                result.setData(uri);
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sendBroadcast(result);
            }
        }
        finish();
    }
}
