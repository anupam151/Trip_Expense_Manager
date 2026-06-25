package com.example.tripexpensemanager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView txtDeveloperBranding = findViewById(R.id.txt_dash_developer_branding);

        // Setup eye-catching developer signature badge text dynamically
        String styledSignatureText = getString(R.string.dev_branding_signature_placeholder, "<b><font color='#1E88E5'>Anupam</font></b>");
        txtDeveloperBranding.setText(Html.fromHtml(styledSignatureText, Html.FROM_HTML_MODE_LEGACY));

        // Setting up a 2-second (2000 milliseconds) delay timer for automatic redirection
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Initializing Intent to navigate from Landing Page (MainActivity) to App Home Page (DashboardActivity)
            Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
            startActivity(intent);

            // Finishing current activity so the user cannot return to the landing screen using the device back button
            finish();
        }, 2000); // 2000 milliseconds = 2 seconds
    }
}