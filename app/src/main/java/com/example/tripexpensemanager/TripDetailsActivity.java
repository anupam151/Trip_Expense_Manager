package com.example.tripexpensemanager;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ImageButton;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import android.widget.Toast;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class TripDetailsActivity extends AppCompatActivity {

    private String tripId;
    private String startDateFromDatabase;
    private String endDateFromDatabase;
    private String membersRaw;

    private LedgerExportManager exportManager;
    private ActivityResultLauncher<Intent> editTripLauncher;

    // --- SAF Launcher for standard "Save to Device" Export ---
    private final ActivityResultLauncher<String> createMasterPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null && exportManager != null) {
                    exportManager.exportAllMembersToSinglePdf(uri, tripId);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        // Initialize the Export Manager
        exportManager = new LedgerExportManager(this, new TripDatabaseHelper(this));

        editTripLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
                            refreshSummaryCards(db);
                            refreshTripDetails();
                        }
                    }
                });

        // Setup Home Button
        ImageButton btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            } else {
                overridePendingTransition(0, 0);
            }
            finish();
        });

        // Setup Add Expense Button
        LinearLayout btnAddExpense = findViewById(R.id.btnAddExpense);
        btnAddExpense.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, AddExpenseActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("IS_EDIT_MODE", false);
            intent.putExtra("TRIP_MEMBERS", this.membersRaw);
            startActivity(intent);
        });

        // Setup Add Payment Button
        LinearLayout btnAddPayment = findViewById(R.id.btnAddPayment);
        if (btnAddPayment != null) {
            btnAddPayment.setOnClickListener(v -> {
                Intent intent = new Intent(TripDetailsActivity.this, AddPaymentActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                intent.putExtra("TRIP_MEMBERS", this.membersRaw);
                startActivity(intent);
            });
        }

        tripId = getIntent().getStringExtra("TRIP_ID");
        String name = getIntent().getStringExtra("TRIP_NAME");
        String dest = getIntent().getStringExtra("DESTINATION");
        String date = getIntent().getStringExtra("START_DATE");
        startDateFromDatabase = getIntent().getStringExtra("START_DATE");
        endDateFromDatabase = getIntent().getStringExtra("END_DATE");
        membersRaw = getIntent().getStringExtra("MEMBERS");

        // Setup Edit Trip Button
        LinearLayout btnEditTrip = findViewById(R.id.btnEditTrip);
        btnEditTrip.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, UpdateTripActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_NAME", ((TextView) findViewById(R.id.txt_details_trip_name)).getText().toString());
            intent.putExtra("TRIP_DESTINATION", ((TextView) findViewById(R.id.txt_details_destination)).getText().toString());
            intent.putExtra("TRIP_START_DATE", this.startDateFromDatabase);
            intent.putExtra("TRIP_END_DATE", this.endDateFromDatabase);
            intent.putExtra("TRIP_MEMBERS", this.membersRaw);
            editTripLauncher.launch(intent);
        });

        // Setup Delete Trip Button
        View btnDeleteTrip = findViewById(R.id.btnDeleteTrip);
        if (btnDeleteTrip != null) {
            btnDeleteTrip.setOnClickListener(v -> {
                AlertDialog alertDialog = new AlertDialog.Builder(this)
                        .setTitle("Delete Trip")
                        .setMessage("Are you sure you want to completely delete this trip?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("Yes, Delete", (dialog, which) -> {
                            try (TripDatabaseHelper dbHelper = new TripDatabaseHelper(this)) {
                                dbHelper.deleteTrip(tripId);
                            }
                            Toast.makeText(this, "Trip deleted successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .create();
                alertDialog.show();
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(0xFF000000);
                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(0xFF000000);
            });
        }

        // --- DIRECT SHARE (Header Icon) ---
        ImageButton btnShareAll = findViewById(R.id.btn_share_all);
        if (btnShareAll != null) {
            btnShareAll.setOnClickListener(v -> {
                if (exportManager != null && tripId != null) {
                    exportManager.shareMasterPdf(tripId);
                }
            });
        }

        // --- LOCAL EXPORT (Bottom Button) ---
        View btnExportAllPdf = findViewById(R.id.btn_all_individual_to_one_pdf);
        if (btnExportAllPdf != null) {
            btnExportAllPdf.setOnClickListener(v -> {
                TextView tripNameView = findViewById(R.id.txt_details_trip_name);
                String safeTripName = "Trip";
                if (tripNameView != null && tripNameView.getText() != null) {
                    safeTripName = tripNameView.getText().toString().replaceAll("[^a-zA-Z0-9]", "_");
                }
                String fileName = safeTripName + "_Master_Ledger.pdf";
                createMasterPdfLauncher.launch(fileName);
            });
        }

        // Setup Complete Ledger Button
        View btnCompleteLedger = findViewById(R.id.btn_complete_ledger);
        if (btnCompleteLedger != null) {
            btnCompleteLedger.setOnClickListener(v -> {
                Intent intent = new Intent(TripDetailsActivity.this, CompleteLedgerActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                startActivity(intent);
            });
        }

        // UI Binding
        if (name != null) {
            ((TextView) findViewById(R.id.txt_details_trip_name)).setText(String.format(Locale.US, "%s", name));
        }
        ((TextView) findViewById(R.id.txt_details_destination)).setText(dest != null ? dest : "N/A");
        ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(date));

        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
            refreshSummaryCards(db);
            ArrayList<String> activeMembers = new ArrayList<>();
            if (membersRaw != null && !membersRaw.isEmpty()) {
                String[] memberList = membersRaw.split(",");
                for (String m : memberList) {
                    if (!m.trim().isEmpty()) activeMembers.add(m.trim());
                }
            }

            ((TextView) findViewById(R.id.txt_details_member_count)).setText(String.valueOf(activeMembers.size()));

            GridLayout gridActive = findViewById(R.id.grid_members);
            gridActive.removeAllViews();
            for (String activeName : activeMembers) {
                addMemberButton(activeName, gridActive, tripId, true);
            }

            GridLayout gridInactive = findViewById(R.id.grid_inactive_members);
            TextView txtInactiveHeader = findViewById(R.id.txt_inactive_members_header);

            ArrayList<String> historicalMembers = getHistoricalMembers(db, tripId);
            ArrayList<String> inactiveMembers = new ArrayList<>();

            for (String hMem : historicalMembers) {
                if (!activeMembers.contains(hMem)) {
                    inactiveMembers.add(hMem);
                }
            }

            if (!inactiveMembers.isEmpty()) {
                txtInactiveHeader.setVisibility(View.VISIBLE);
                gridInactive.setVisibility(View.VISIBLE);
                gridInactive.removeAllViews();
                for (String inactiveName : inactiveMembers) {
                    addMemberButton(inactiveName, gridInactive, tripId, false);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tripId != null) {
            try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
                refreshSummaryCards(db);
                refreshTripDetails();
            }
        }
    }

    private void refreshSummaryCards(TripDatabaseHelper db) {
        ((TextView) findViewById(R.id.txt_details_fund_balance)).setText(String.format(Locale.US, "₹%.2f", db.getFundBalance(tripId)));
        ((TextView) findViewById(R.id.txt_details_total_expenses)).setText(String.format(Locale.US, "₹%.2f", db.getTripTotalExpenses(tripId)));
        ((TextView) findViewById(R.id.txt_details_total_receipts)).setText(String.format(Locale.US, "₹%.2f", db.getTripTotalPaymentsReceived(tripId)));
    }

    private void addMemberButton(String mName, GridLayout grid, String tripId, boolean isActive) {
        Button btn = new Button(this);
        btn.setText(mName);
        btn.setAllCaps(false);

        if (!isActive) {
            btn.setTextColor(Color.parseColor("#757575"));
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
        }

        btn.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, MemberLedgerActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("MEMBER_NAME", mName);
            startActivity(intent);
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(8, 8, 8, 8);
        btn.setLayoutParams(params);
        grid.addView(btn);
    }

    private ArrayList<String> getHistoricalMembers(TripDatabaseHelper db, String tripId) {
        ArrayList<String> members = new ArrayList<>();
        String query = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ? " +
                "UNION SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";
        try (Cursor c = db.getReadableDatabase().rawQuery(query, new String[]{tripId, tripId})) {
            while (c.moveToNext()) {
                String name = c.getString(0).trim();
                if (!"Fund".equalsIgnoreCase(name) && !members.contains(name)) members.add(name);
            }
        }
        String sharedQuery = "SELECT expense_shared_with FROM expenses WHERE expense_trip_id = ?";
        try (Cursor c = db.getReadableDatabase().rawQuery(sharedQuery, new String[]{tripId})) {
            while (c.moveToNext()) {
                String sharedStr = c.getString(0);
                if (sharedStr != null && !sharedStr.isEmpty()) {
                    for (String s : sharedStr.split(",")) {
                        String cleanName = s.trim();
                        if (!cleanName.isEmpty() && !members.contains(cleanName)) {
                            members.add(cleanName);
                        }
                    }
                }
            }
        }
        return members;
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "N/A";
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yy", Locale.US);
        try {
            Date date = inputFormat.parse(dateStr);
            if (date != null) return outputFormat.format(date);
        } catch (ParseException e) {
            Log.e("TripDetailsActivity", "Error parsing date: " + dateStr, e);
        }
        return dateStr;
    }

    private void refreshTripDetails() {
        try (TripDatabaseHelper db = new TripDatabaseHelper(this);
             Cursor cursor = db.getReadableDatabase().rawQuery(
                     "SELECT * FROM " + TripDatabaseHelper.TABLE_TRIPS + " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?",
                     new String[]{tripId})) {

            if (cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_NAME));
                String dest = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_DESTINATION));
                String sDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_START_DATE));
                String eDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_END_DATE));
                String currentMembersRaw = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBERS));

                ((TextView) findViewById(R.id.txt_details_trip_name)).setText(name);
                ((TextView) findViewById(R.id.txt_details_destination)).setText(dest);
                ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(sDate));

                startDateFromDatabase = sDate;
                endDateFromDatabase = eDate;

                ArrayList<String> activeMembers = new ArrayList<>();
                if (currentMembersRaw != null && !currentMembersRaw.isEmpty()) {
                    for (String m : currentMembersRaw.split(",")) {
                        if (!m.trim().isEmpty()) activeMembers.add(m.trim());
                    }
                }

                ((TextView) findViewById(R.id.txt_details_member_count)).setText(String.valueOf(activeMembers.size()));

                GridLayout gridActive = findViewById(R.id.grid_members);
                gridActive.removeAllViews();
                for (String activeName : activeMembers) {
                    addMemberButton(activeName, gridActive, tripId, true);
                }

                GridLayout gridInactive = findViewById(R.id.grid_inactive_members);
                TextView txtInactiveHeader = findViewById(R.id.txt_inactive_members_header);

                ArrayList<String> historicalMembers = getHistoricalMembers(db, tripId);
                ArrayList<String> inactiveMembers = new ArrayList<>();
                for (String hMem : historicalMembers) {
                    if (!activeMembers.contains(hMem)) inactiveMembers.add(hMem);
                }

                if (!inactiveMembers.isEmpty()) {
                    txtInactiveHeader.setVisibility(View.VISIBLE);
                    gridInactive.setVisibility(View.VISIBLE);
                    gridInactive.removeAllViews();
                    for (String inactiveName : inactiveMembers) {
                        addMemberButton(inactiveName, gridInactive, tripId, false);
                    }
                } else {
                    txtInactiveHeader.setVisibility(View.GONE);
                    gridInactive.setVisibility(View.GONE);
                }
                membersRaw = currentMembersRaw;
            }
        }
    }
}