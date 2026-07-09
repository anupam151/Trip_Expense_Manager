package com.example.tripexpensemanager;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.appcompat.app.AlertDialog;

public class DashboardActivity extends AppCompatActivity {

    private TextView lblRecentHeading;
    private LinearLayout containerPinnedTripsStack, layoutNoPinnedTrips;
    private TripDatabaseHelper dbHelper;
    private DrawerLayout drawerLayout;

    private final ActivityResultLauncher<String> backupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),
            uri -> { if (uri != null) performBackup(uri); });

    private final ActivityResultLauncher<String[]> restoreLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) performRestore(uri); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // 1. Initialize helper immediately
        dbHelper = new TripDatabaseHelper(this);

        // 2. Optimized First-Run Check
        android.content.SharedPreferences appPrefs = getSharedPreferences("app_internal_prefs", MODE_PRIVATE);

        // Simplified using the '!' operator
        if (!appPrefs.getBoolean("is_first_launch_done", false)) {
            // Wipe data on first run
            android.database.sqlite.SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_TRIPS);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_EXPENSES);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_PAYMENTS);
            db.execSQL("DELETE FROM " + TripDatabaseHelper.TABLE_TRIP_MEMBERS);

            // Mark that the first run is complete
            appPrefs.edit().putBoolean("is_first_launch_done", true).apply();
        }
        setContentView(R.layout.activity_dashboard);

        dbHelper = new TripDatabaseHelper(this);

        drawerLayout = findViewById(R.id.drawer_layout);
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        NavigationView navView = findViewById(R.id.nav_view);

        lblRecentHeading = findViewById(R.id.lbl_recent_trip_heading);
        containerPinnedTripsStack = findViewById(R.id.container_pinned_trips_stack);
        layoutNoPinnedTrips = findViewById(R.id.layout_no_pinned_trips);

        TextView txtDeveloperBranding = findViewById(R.id.txt_dash_developer_branding);
        String styledSignatureText = getString(R.string.dev_branding_signature_placeholder, "<b><font color='#1E88E5'>Anupam</font></b>");
        txtDeveloperBranding.setText(Html.fromHtml(styledSignatureText, Html.FROM_HTML_MODE_LEGACY));

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

            if (id == R.id.nav_create_trip) {
                startActivity(new Intent(this, CreateTripActivity.class));
            } else if (id == R.id.nav_view_trips) {
                startActivity(new Intent(this, TripListActivity.class));
            } else if (id == R.id.nav_about) {
                startActivity(new Intent(this, AboutActivity.class));
            } else if (id == R.id.nav_backup) {
                backupLauncher.launch("TripManager_Backup.db");
            } else if (id == R.id.nav_restore) {
                restoreLauncher.launch(new String[]{"application/octet-stream"});
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        updatePinnedWorkspace();
    }

    private void performBackup(Uri uri) {
        try (FileInputStream fis = new FileInputStream(getDatabasePath("TripManager.db"));
             OutputStream fos = getContentResolver().openOutputStream(uri)) {
            if (fos != null) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) fos.write(buffer, 0, length);
                Toast.makeText(this, "Backup Successful!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Backup Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void performRestore(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(getDatabasePath("TripManager.db"))) {
            dbHelper.close();
            if (is != null) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) fos.write(buffer, 0, length);
                Toast.makeText(this, "Restore Successful! Restarting...", Toast.LENGTH_LONG).show();
                finishAffinity();
                startActivity(new Intent(this, DashboardActivity.class));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Restore Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updatePinnedWorkspace() {
        containerPinnedTripsStack.removeAllViews();
        Cursor cursor = dbHelper.getPinnedTripsCursor();

        // 1. EMPTY STATE LOGIC
        if (cursor == null || cursor.getCount() == 0) {
            lblRecentHeading.setVisibility(View.GONE);
            containerPinnedTripsStack.setVisibility(View.GONE); // Hide the stack
            layoutNoPinnedTrips.setVisibility(View.VISIBLE);    // Show the placeholder

            if (cursor != null) cursor.close();
            return;
        }

        // 2. ACTIVE STATE LOGIC
        layoutNoPinnedTrips.setVisibility(View.GONE);       // Hide the placeholder
        containerPinnedTripsStack.setVisibility(View.VISIBLE); // Show the stack
        lblRecentHeading.setVisibility(View.VISIBLE);

        int itemIndex = 1;
        float scale = getResources().getDisplayMetrics().density;
        int marginHorizontalPx = Math.round(2 * scale);
        int marginBottomPx = Math.round(8 * scale);

        while (cursor.moveToNext()) {
            // --- NEW: Strictly limit the dashboard to exactly 1 pinned trip ---
            if (itemIndex > 1) {
                break;
            }
            // ------------------------------------------------------------------

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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}