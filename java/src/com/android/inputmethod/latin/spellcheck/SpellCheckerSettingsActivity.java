/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.spellcheck;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.inputmethod.latin.setup.CrazeDashboardActivity;

/** Opens the keyboard's text correction settings from Android spell-checker settings. */
public final class SpellCheckerSettingsActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent dashboard = new Intent(this, CrazeDashboardActivity.class);
        dashboard.putExtra(CrazeDashboardActivity.EXTRA_START_TAB, 2);
        dashboard.putExtra(CrazeDashboardActivity.EXTRA_START_PAGE, 6);
        startActivity(dashboard);
        finish();
    }
}
