package com.example.tripexpensemanager;

import android.content.Intent;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;

// --- FIX 1: Tell Android Studio to ignore the deprecation warnings for now ---
@SuppressWarnings("deprecation")
public abstract class BaseDrawerActivity extends AppCompatActivity {

    protected DrawerLayout drawerLayout;
    protected NavigationView navView;
    private GoogleSignInClient mGoogleSignInClient;

    // Google Sign-In Launcher
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    Toast.makeText(this, "Signed in as: " + account.getEmail(), Toast.LENGTH_SHORT).show();
                    updateSignInUI();
                    onUserSuccessfullySignedIn(account); // Notify child activities
                } catch (ApiException e) {
                    Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show();
                }
            });

    // 1. Child activities call this to wire up the universal drawer
    protected void setupUniversalDrawer(int drawerId, int navViewId) {
        drawerLayout = findViewById(drawerId);
        navView = findViewById(navViewId);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        if (navView != null) {
            navView.setNavigationItemSelectedListener(this::handleNavigationItemSelected);
        }
        updateSignInUI();
    }

    // 2. Child activities can OVERRIDE this if they need to refresh data after a login!
    protected void onUserSuccessfullySignedIn(GoogleSignInAccount account) {
        // Intentionally empty default implementation
    }

    // 3. Centralized Click Handling
    // 3. Centralized Click Handling
    private boolean handleNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_login_toggle) {
            if (GoogleSignIn.getLastSignedInAccount(this) != null) {
                signOut();
            } else {
                signInLauncher.launch(mGoogleSignInClient.getSignInIntent());
            }
        } else if (id == R.id.nav_create_trip) {
            navigateSafely(CreateTripActivity.class);
        } else if (id == R.id.nav_view_trips) {
            navigateSafely(TripListActivity.class);
        } else if (id == R.id.nav_archived_trips) {           // 🟢 ADDED THIS BLOCK
            navigateSafely(ArchivedTripsActivity.class);      // 🟢 NAVIGATES TO ARCHIVE
        } else if (id == R.id.nav_utility) {
            navigateSafely(UtilityActivity.class);
        } else if (id == R.id.nav_settings) {
            navigateSafely(SettingsActivity.class);
        } else if (id == R.id.nav_about) {
            navigateSafely(AboutActivity.class);
        } else if (id == R.id.nav_home) {
            navigateSafely(DashboardActivity.class);
        }

        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    // Prevents opening multiple copies of the same screen!
    private void navigateSafely(Class<?> targetActivity) {
        if (!this.getClass().equals(targetActivity)) {
            Intent intent = new Intent(this, targetActivity);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }

    // 4. Centralized UI Updates (Profile Pic + Text)
    protected void updateSignInUI() {
        if (navView == null) return;
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        android.view.View headerView = navView.getHeaderView(0);

        if (headerView != null) {
            TextView txtTitle = headerView.findViewById(R.id.txt_login_title);
            TextView txtDesc = headerView.findViewById(R.id.txt_login_desc);
            android.widget.ImageView imgProfile = headerView.findViewById(R.id.img_profile_picture);

            if (account != null) {
                if (txtTitle != null) txtTitle.setText(account.getDisplayName() != null ? account.getDisplayName() : "User");
                if (txtDesc != null) {
                    txtDesc.setText(account.getEmail());
                    txtDesc.setAlpha(0.9f);
                }
                if (imgProfile != null) {
                    android.net.Uri photoUri = account.getPhotoUrl();
                    if (photoUri != null) {
                        imgProfile.clearColorFilter();
                        imgProfile.setImageTintList(null);
                        new Thread(() -> {
                            try {
                                java.io.InputStream in = new java.net.URL(photoUri.toString()).openStream();
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);
                                runOnUiThread(() -> imgProfile.setImageBitmap(bitmap));
                            } catch (Exception e) {
                                android.util.Log.e("BaseDrawerActivity", "Failed to load Google profile photo", e);
                            }
                        }).start();
                    } else {
                        imgProfile.setImageResource(R.drawable.person);
                        imgProfile.setColorFilter(android.graphics.Color.WHITE);
                    }
                }
            } else {
                if (txtTitle != null) txtTitle.setText("Guest User");
                if (txtDesc != null) {
                    txtDesc.setText("Log in to backup trips");
                    txtDesc.setAlpha(0.8f);
                }
                if (imgProfile != null) {
                    imgProfile.setImageResource(R.drawable.person);
                    imgProfile.setColorFilter(android.graphics.Color.WHITE);
                }
            }
        }

        android.view.Menu menu = navView.getMenu();
        android.view.MenuItem loginToggleItem = menu.findItem(R.id.nav_login_toggle);
        if (loginToggleItem != null) {
            if (account != null) {
                loginToggleItem.setTitle("Log Out");
                loginToggleItem.setIcon(android.R.drawable.ic_lock_power_off);
            } else {
                loginToggleItem.setTitle("Log In with Google");
                loginToggleItem.setIcon(android.R.drawable.ic_menu_myplaces);
            }
        }
    }

    // 5. Centralized Logout
    private void signOut() {
        android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                // --- FIX 2: Expression Lambda used here to remove warnings ---
                .setPositiveButton("Yes, Log Out", (dialog, which) ->
                        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                            updateSignInUI();
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                            Toast.makeText(this, "Successfully Logged Out", Toast.LENGTH_SHORT).show();
                        })
                )
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .create();

        alertDialog.show();
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
}