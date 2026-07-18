package com.example.tripexpensemanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Profile Information Click Listener using Lambda
        findViewById(R.id.btn_setting_profile).setOnClickListener(v -> startActivity(new Intent(SettingsActivity.this, ProfileActivity.class)));
    }
}