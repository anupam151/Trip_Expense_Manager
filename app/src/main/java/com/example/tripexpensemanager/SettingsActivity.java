package com.example.tripexpensemanager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executor;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Link the Views (Declared as local variables as per best practices)
        TextView btnSignOut = findViewById(R.id.btn_setting_signout);
        TextView btnDeleteAccount = findViewById(R.id.btn_setting_delete_account);
        android.view.View btnAbout = findViewById(R.id.btn_dash_about);
        SwitchCompat biometricSwitch = findViewById(R.id.switch_biometric_lock);

        // Profile Information Click Listener
        findViewById(R.id.btn_setting_profile).setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, ProfileActivity.class)));

        // About Click Listener
        btnAbout.setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, AboutActivity.class)));

        // Danger Zone Click Listeners
        btnSignOut.setOnClickListener(v -> signOutUser());
        btnDeleteAccount.setOnClickListener(v -> showDeleteConfirmationDialog());

        // Load saved state for Biometric Lock
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        biometricSwitch.setChecked(prefs.getBoolean("biometric_enabled", false));



        // Handle Biometric toggle
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

        BiometricPrompt.AuthenticationCallback authCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
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

        // Initialize Prompt with the context, executor, and the callback
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, authCallback);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Identity")
                .setSubtitle("Authenticate to enable App Lock")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    @SuppressWarnings("deprecation")
    private void signOutUser() {
        android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Log Out", (dialog, which) -> {

                    // 1. Sign out from Firebase
                    mAuth.signOut();

                    // 2. Sign out from Google Play Services
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .build();
                    GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(SettingsActivity.this, gso);

                    googleSignInClient.signOut().addOnCompleteListener(this, task -> {
                        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                        Toast.makeText(SettingsActivity.this, "Successfully Logged Out", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .create();

        alertDialog.show();

        // 3. Apply your custom styling to the buttons
        android.widget.Button btnPositive = alertDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        android.widget.Button btnNegative = alertDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);

        if (btnPositive != null) {
            btnPositive.setTextColor(android.graphics.Color.parseColor("#85022E"));
            btnPositive.setAllCaps(false);
        }
        if (btnNegative != null) {
            btnNegative.setTextColor(android.graphics.Color.parseColor("#85022E"));
            btnNegative.setAllCaps(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void deleteFirebaseAccount() {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null && user.getEmail() != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String userEmail = user.getEmail();

            db.collection("Trips")
                    .whereEqualTo("ownerEmail", userEmail)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {

                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot document : queryDocumentSnapshots) {
                            batch.delete(document.getReference());
                        }

                        batch.commit()
                                .addOnSuccessListener(aVoid -> {

                                    // Try to delete the Auth account
                                    user.delete()
                                            .addOnSuccessListener(aVoid2 -> {
                                                // Success! Clear Google Sign-In session
                                                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                        .requestEmail()
                                                        .build();
                                                GoogleSignIn.getClient(SettingsActivity.this, gso).signOut();

                                                Toast.makeText(SettingsActivity.this, "Account and all trips permanently deleted", Toast.LENGTH_SHORT).show();

                                                Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                startActivity(intent);
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
                                                // THE FIX: Handle the Recent Login Security Requirement
                                                Toast.makeText(SettingsActivity.this,
                                                        "For security, Firebase requires a recent login to delete an account. Please log in again to finish.",
                                                        Toast.LENGTH_LONG).show();

                                                // Automatically trigger your existing sign-out logic so they can get a fresh session!
                                                signOutUser();
                                            });
                                })
                                .addOnFailureListener(e ->Toast.makeText(SettingsActivity.this, "Failed to delete trips from database: " + e.getMessage(), Toast.LENGTH_LONG).show());

                    })
                    .addOnFailureListener(e ->Toast.makeText(SettingsActivity.this, "Permission Denied: Could not read trips. " + e.getMessage(), Toast.LENGTH_LONG).show());
        } else {
            Toast.makeText(this, "Error: User email not found", Toast.LENGTH_SHORT).show();
        }
    }
    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account? You will lose access to all your trips. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteFirebaseAccount())
                .setNegativeButton("Cancel", null) // Does nothing, just dismisses the dialog
                .show();
    }

}