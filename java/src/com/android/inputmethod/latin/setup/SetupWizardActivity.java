/* Copyright (C) 2026 The CrazeStudio Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.android.inputmethod.latin.setup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Android may still invoke the historical setup component; route into the CBoard setup UI. */
public final class SetupWizardActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent dashboard = new Intent(this, CrazeDashboardActivity.class);
        dashboard.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(dashboard);
        finish();
    }
}
