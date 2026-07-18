package com.example.tripexpensemanager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity; // Changed from Activity to AppCompatActivity
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

// 1. MUST extend AppCompatActivity for BiometricPrompt to work
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Profile Information Click Listener using Lambda
        findViewById(R.id.btn_setting_profile).setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, ProfileActivity.class)));

        SwitchCompat biometricSwitch = findViewById(R.id.switch_biometric_lock);

        // Load saved state
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        biometricSwitch.setChecked(prefs.getBoolean("biometric_enabled", false));

        // Handle toggle
        biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Trigger verification before enabling
                authenticateUser(success -> {
                    if (success) {
                        prefs.edit().putBoolean("biometric_enabled", true).apply();
                    } else {
                        biometricSwitch.setChecked(false); // Revert if auth failed
                    }
                });
            } else {
                prefs.edit().putBoolean("biometric_enabled", false).apply();
            }
        });
    }

    private void authenticateUser(java.util.function.Consumer<Boolean> callback) {
        Executor executor = ContextCompat.getMainExecutor(this);

        // 2. Extracted the callback to fix the initialization error and added @NonNull
        BiometricPrompt.AuthenticationCallback authCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                // Fixed: Pass 'result', not 'biometricPrompt.AuthenticationResult'
                super.onAuthenticationSucceeded(result);
                callback.accept(true);
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                callback.accept(false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // Authentication failed (wrong finger/pin), do not call back yet so they can try again
            }
        };

        // Initialize Prompt with the context, executor, and the callback we just created
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, authCallback);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Identity")
                .setSubtitle("Authenticate to enable App Lock")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}