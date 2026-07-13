package com.example.tripexpensemanager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Suppressing the warning for this specific block
            @SuppressWarnings("deprecation")
            boolean isSignedIn = GoogleSignIn.getLastSignedInAccount(this) != null;

            // 1. Determine the destination class first
            Class<?> destinationClass = isSignedIn ? DashboardActivity.class : LoginActivity.class;

// 2. Write the Intent logic once
            startActivity(new Intent(MainActivity.this, destinationClass));

// 3. Finish activity
            finish();
        }, 1000);
    }
}