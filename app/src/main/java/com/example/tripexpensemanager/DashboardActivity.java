package com.example.tripexpensemanager;

import android.content.Intent;
import android.database.Cursor;
//import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

// --- NEW: Google Drive & API Imports ---
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import java.util.Collections;

//import java.io.FileInputStream;
import java.io.FileOutputStream;
//import java.io.InputStream;
//import java.io.OutputStream;
//import android.util.Log;

import androidx.appcompat.app.AlertDialog;


@SuppressWarnings("deprecation")
public class DashboardActivity extends AppCompatActivity {

    private TextView lblRecentHeading;
    private LinearLayout containerPinnedTripsStack, layoutNoPinnedTrips;
    private TripDatabaseHelper dbHelper;
    private DrawerLayout drawerLayout;
    private NavigationView navView;

    // Google Sign-In variables
    private GoogleSignInClient mGoogleSignInClient;
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    Toast.makeText(this, "Signed in as: " + account.getEmail(), Toast.LENGTH_SHORT).show();
                    updateSignInUI(); // Update menu text to "Log Out"

                    // --- NEW: Automatically trigger restore on successful sign in ---
                    restoreDatabaseFromDrive();

                } catch (ApiException e) {
                    Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 3. Auto-Restore Trigger:
        if (getIntent().getBooleanExtra("RESTORE_DATA", false)) {
            // Set the value to false so it won't trigger again if the activity rotates
            getIntent().putExtra("RESTORE_DATA", false);

            // Using the clean method reference
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::restoreDatabaseFromDrive, 400);
        }

        // 2. Set the UI layout first so views can be found
        setContentView(R.layout.activity_dashboard);

        // This block should be standalone, NOT inside an 'if' or after heavy database work
        if (getIntent().getBooleanExtra("RESTORE_DATA", false)) {
            getIntent().removeExtra("RESTORE_DATA");

            // Using the clean method reference
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::restoreDatabaseFromDrive, 800);
        }

        // 4. Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestScopes(new com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file"))
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 5. Database Cleanup (One-time on first launch)
        dbHelper = new TripDatabaseHelper(this);
        android.content.SharedPreferences appPrefs = getSharedPreferences("app_internal_prefs", MODE_PRIVATE);
        if (!appPrefs.getBoolean("is_first_launch_done", false)) {
            android.database.sqlite.SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_TRIPS);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_EXPENSES);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_PAYMENTS);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_TRIP_MEMBERS);
            appPrefs.edit().putBoolean("is_first_launch_done", true).apply();
        }

        // 6. Initialize UI Components
        drawerLayout = findViewById(R.id.drawer_layout);
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        navView = findViewById(R.id.nav_view);
        lblRecentHeading = findViewById(R.id.lbl_recent_trip_heading);
        containerPinnedTripsStack = findViewById(R.id.container_pinned_trips_stack);
        layoutNoPinnedTrips = findViewById(R.id.layout_no_pinned_trips);

        // 7. Branding
        TextView txtDeveloperBranding = findViewById(R.id.txt_dash_developer_branding);
        String styledSignatureText = getString(R.string.dev_branding_signature_placeholder, "<b><font color='#1E88E5'>Anupam</font></b>");
        txtDeveloperBranding.setText(Html.fromHtml(styledSignatureText, Html.FROM_HTML_MODE_LEGACY));

        // 8. Listeners and Navigation
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
                else finish();
            }
        });

        btnOpenDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.btn_dash_create_trip).setOnClickListener(v -> startActivity(new Intent(this, CreateTripActivity.class)));
        findViewById(R.id.btn_dash_view_trips).setOnClickListener(v -> startActivity(new Intent(this, TripListActivity.class)));
        findViewById(R.id.btn_create_new_trips).setOnClickListener(v -> startActivity(new Intent(this, CreateTripActivity.class)));

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_login) {
                if (GoogleSignIn.getLastSignedInAccount(this) != null) signOut();
                else signInWithGoogle();
            } else if (id == R.id.nav_create_trip) {
                startActivity(new Intent(this, CreateTripActivity.class));
            } else if (id == R.id.nav_view_trips) {
                startActivity(new Intent(this, TripListActivity.class));
            } else if (id == R.id.nav_about) {
                startActivity(new Intent(this, AboutActivity.class));
            } else if (id == R.id.nav_backup) {
                backupDatabaseToDrive();
            } else if (id == R.id.nav_restore) {
                restoreDatabaseFromDrive();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // 9. Final UI Updates
        updatePinnedWorkspace();
        updateSignInUI();
    }

    // --- NEW: Cloud Backup Method ---
    private void backupDatabaseToDrive() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Please sign in to Google first!", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Preparing upload...", Toast.LENGTH_SHORT).show();

        // Prepare credentials for Drive
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                this, Collections.singletonList("https://www.googleapis.com/auth/drive.file"));
        credential.setSelectedAccount(account.getAccount());

        Drive driveService = new Drive.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("TripExpenseManager")
                .build();

        // Run the upload in a background thread to prevent app freeze
        new Thread(() -> {
            try {
                GoogleDriveService driveUploader = new GoogleDriveService(driveService);
                java.io.FileInputStream fis = new java.io.FileInputStream(getDatabasePath("TripManager.db"));

                // Just run the upload. No need to store the return value!
                driveUploader.uploadDatabase(fis, "TripManager_Backup.db");

                runOnUiThread(() -> Toast.makeText(DashboardActivity.this, "Backup Successful", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(DashboardActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // --- NEW: Cloud Restore Method ---
    public void restoreDatabaseFromDrive() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "Please sign in to Google first!", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Searching for backup in Cloud...", Toast.LENGTH_SHORT).show();

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                this, Collections.singletonList("https://www.googleapis.com/auth/drive.file"));
        credential.setSelectedAccount(account.getAccount());

        Drive driveService = new Drive.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName("TripExpenseManager")
                .build();

        new Thread(() -> {
            try {
                GoogleDriveService driveUploader = new GoogleDriveService(driveService);

                // 1. Find the backup file ID
                String fileId = driveUploader.getLatestBackupFileId("TripManager_Backup.db");

                if (fileId == null) {
                    runOnUiThread(() -> Toast.makeText(DashboardActivity.this, "No backup found in Google Drive!", Toast.LENGTH_LONG).show());
                    return;
                }

                runOnUiThread(() -> Toast.makeText(DashboardActivity.this, "Downloading backup...", Toast.LENGTH_SHORT).show());

                // 2. Shut down the local database temporarily
                dbHelper.close();

                // 3. Open a stream to overwrite the local database file
                java.io.File localDbFile = getDatabasePath("TripManager.db");
                FileOutputStream fos = new FileOutputStream(localDbFile);

                // 4. Download and save
                driveUploader.downloadFile(fileId, fos);
                fos.close();

                // 5. Restart the app to apply the newly downloaded database
                runOnUiThread(() -> {
                    Toast.makeText(DashboardActivity.this, "Restore Successful!", Toast.LENGTH_LONG).show();
                    finishAffinity();
                    startActivity(new Intent(DashboardActivity.this, DashboardActivity.class));
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(DashboardActivity.this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // --- EXISTING METHODS BELOW ---

    private void updateSignInUI() {
        Menu menu = navView.getMenu();
        MenuItem loginItem = menu.findItem(R.id.nav_login);
        MenuItem emailItem = menu.findItem(R.id.nav_user_email);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // User is signed in
            loginItem.setTitle("Log Out\n");

            if (emailItem != null) {
                // Create a SpannableString from the email
                SpannableString styledEmail = new SpannableString(account.getEmail());

                // 1. Change the color (e.g., to a muted gray)
                styledEmail.setSpan(new ForegroundColorSpan(Color.parseColor("#808080")), 0, styledEmail.length(), 0);

                // 2. Reduce the font size to 80% to ensure it fits on one line
                styledEmail.setSpan(new RelativeSizeSpan(0.8f), 0, styledEmail.length(), 0);

                // Apply the styled text to the menu item
                emailItem.setTitle(styledEmail);
                emailItem.setVisible(true);
            }
        } else {
            // User is logged out
            loginItem.setTitle("Google Sign-In");

            if (emailItem != null) {
                emailItem.setVisible(false);
            }
        }
    }

    // --- UPDATED: Sign Out with Warning Dialog ---
    private void signOut() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out & Clear Data")
                .setMessage("Logging out will erase all local trip data from this device to protect your privacy.\n\nPlease ensure you have tapped 'Backup' to save your latest changes to Google Drive before continuing.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Log Out", (dialog, which) -> performActualSignOut())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void performActualSignOut() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // 1. Wipe local data
            android.database.sqlite.SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_TRIPS);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_EXPENSES);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_PAYMENTS);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_TRIP_MEMBERS);

            // 2. Clear UI
            updatePinnedWorkspace();
            updateSignInUI();

            // 3. FORCE REDIRECT
            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            // Clear all previous activities so they can't go "back" to the Dashboard
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            Toast.makeText(DashboardActivity.this, "Logged Out", Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePinnedWorkspace() {
        containerPinnedTripsStack.removeAllViews();
        Cursor cursor = dbHelper.getPinnedTripsCursor();

        if (cursor == null || cursor.getCount() == 0) {
            lblRecentHeading.setVisibility(View.GONE);
            containerPinnedTripsStack.setVisibility(View.GONE);
            layoutNoPinnedTrips.setVisibility(View.VISIBLE);
            if (cursor != null) cursor.close();
            return;
        }

        layoutNoPinnedTrips.setVisibility(View.GONE);
        containerPinnedTripsStack.setVisibility(View.VISIBLE);
        lblRecentHeading.setVisibility(View.VISIBLE);

        int itemIndex = 1;
        float scale = getResources().getDisplayMetrics().density;
        int marginHorizontalPx = Math.round(2 * scale);
        int marginBottomPx = Math.round(8 * scale);

        while (cursor.moveToNext()) {
            if (itemIndex > 1) break;

            String tripId = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_ID));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_NAME));
            String destination = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_DESTINATION));
            String members = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBERS));
            int count = cursor.getInt(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBER_COUNT));
            String startDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_START_DATE));
            String endDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_END_DATE));

            TripModel trip = new TripModel(tripId, name, destination, members, count, startDate, endDate);
            trip.setIsPinnedState(1);

            View cardView = LayoutInflater.from(this).inflate(R.layout.item_trip, containerPinnedTripsStack, false);

            TextView txtTripName = cardView.findViewById(R.id.txt_item_trip_name);
            TextView txtDestination = cardView.findViewById(R.id.txt_item_destination);
            TextView txtMemberCount = cardView.findViewById(R.id.txt_item_member_count);
            TextView txtFundBalance = cardView.findViewById(R.id.txt_item_fund_balance);
            TextView txtStartDate = cardView.findViewById(R.id.txt_item_start_date);

            TextView btnPin = cardView.findViewById(R.id.btn_item_pin);
            TextView btnEdit = cardView.findViewById(R.id.btn_item_edit);
            TextView btnDelete = cardView.findViewById(R.id.btn_item_delete);
            MaterialButton btnAddExpense = cardView.findViewById(R.id.btn_item_add_expense);
            MaterialButton btnAddPayment = cardView.findViewById(R.id.btn_item_add_payment);

            double totalExpense = dbHelper.getTripTotalExpenses(tripId);
            double totalReceived = dbHelper.getTripTotalPaymentsReceived(tripId);
            double fundBalance = dbHelper.getFundBalance(tripId);

            TextView txtTotalExpense = cardView.findViewById(R.id.txt_item_total_expense);
            TextView txtTotalReceived = cardView.findViewById(R.id.txt_item_total_received);

            if (txtTotalExpense != null) txtTotalExpense.setText(getString(R.string.fmt_dash_currency_rupees, totalExpense));
            if (txtTotalReceived != null) txtTotalReceived.setText(getString(R.string.fmt_dash_currency_rupees, totalReceived));
            if (txtFundBalance != null) txtFundBalance.setText(String.format(java.util.Locale.US, "₹%.2f", fundBalance));

            txtTripName.setText(getString(R.string.fmt_dash_pinned_title, itemIndex, name));
            txtDestination.setText(getString(R.string.fmt_dash_destination, destination));
            txtMemberCount.setText(getString(R.string.fmt_dash_member_count, count));
            txtStartDate.setText(getString(R.string.fmt_dash_start_date, startDate));

            btnPin.setText(getString(R.string.action_unpin));
            btnPin.setTextColor(0xFF2E7D32);

            cardView.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, TripDetailsActivity.class);
                intent.putExtra("TRIP_ID", trip.getTripId());
                intent.putExtra("TRIP_NAME", trip.getTripName());
                intent.putExtra("DESTINATION", trip.getDestination());
                intent.putExtra("START_DATE", trip.getStartDate());
                intent.putExtra("END_DATE", trip.getEndDate());
                intent.putExtra("MEMBERS", trip.getMembersListString());
                startActivity(intent);
            });

            btnPin.setOnClickListener(v -> {
                dbHelper.toggleTripPinStatus(trip.getTripId());
                Toast.makeText(DashboardActivity.this, "'" + name + "' unpinned successfully!", Toast.LENGTH_SHORT).show();
                updatePinnedWorkspace();
                DashboardActivity.triggerAutoBackup(this);
            });

            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, UpdateTripActivity.class);
                intent.putExtra("TRIP_ID", trip.getTripId());
                intent.putExtra("TRIP_NAME", trip.getTripName());
                intent.putExtra("TRIP_DESTINATION", trip.getDestination());
                intent.putExtra("TRIP_MEMBERS", trip.getMembersListString());
                intent.putExtra("TRIP_START_DATE", trip.getStartDate());
                intent.putExtra("TRIP_END_DATE", trip.getEndDate());
                startActivity(intent);
            });

            btnDelete.setOnClickListener(v -> {
                AlertDialog alertDialog = new AlertDialog.Builder(DashboardActivity.this)
                        .setTitle("Delete Trip")
                        .setMessage("Are you sure you want to delete '" + name + "'?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("Yes, Delete", (dialog, which) -> {
                            dbHelper.deleteTrip(tripId);
                            updatePinnedWorkspace();
                            DashboardActivity.triggerAutoBackup(this);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create();
                alertDialog.show();
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF000000);
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF000000);
            });

            btnAddExpense.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AddExpenseActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                intent.putExtra("TRIP_MEMBERS", members);
                startActivity(intent);
            });

            btnAddPayment.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AddPaymentActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                intent.putExtra("TRIP_MEMBERS", members);
                startActivity(intent);
            });

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            layoutParams.setMargins(marginHorizontalPx, marginBottomPx, marginHorizontalPx, marginBottomPx);
            cardView.setLayoutParams(layoutParams);

            containerPinnedTripsStack.addView(cardView);
            itemIndex++;
        }
        cursor.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePinnedWorkspace();
        updateSignInUI(); // Ensure UI is correct when coming back to the screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    // --- NEW: Global Static Trigger for Auto-Backup ---
    // --- UPDATED: Bulletproof Offline-First Auto-Backup ---
    public static void triggerAutoBackup(android.content.Context context) {
        // 1. Define the rule: ONLY run when the phone has internet
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        // 2. Build the work request pointing to our new class
        androidx.work.OneTimeWorkRequest backupRequest = new androidx.work.OneTimeWorkRequest.Builder(DriveBackupWorker.class)
                .setConstraints(constraints)
                .build();

        // 3. Queue it up! Using "REPLACE" ensures that if a user makes 5 quick edits offline,
        // it only uploads the database once when they reconnect, saving data and battery.
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "DriveBackupWork",
                androidx.work.ExistingWorkPolicy.REPLACE,
                backupRequest
        );
    }
}