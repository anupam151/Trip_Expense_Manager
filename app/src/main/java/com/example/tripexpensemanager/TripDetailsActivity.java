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

public class TripDetailsActivity extends AppCompatActivity {

    private String tripId; // Defined at class level for access in onResume
    private String startDateFromDatabase;
    private String endDateFromDatabase;
    private String membersRaw;
    private androidx.activity.result.ActivityResultLauncher<Intent> editTripLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        editTripLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
                            refreshSummaryCards(db);
                            refreshTripDetails();
                        }
                    }
                });

        //Setup Home Button
        ImageButton btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            } else {
                overridePendingTransition(0, 0);
            }
            finish();
        });


        // Setup Refresh Button
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(v -> {
            try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
                refreshSummaryCards(db);
                // Add a small visual feedback so the user knows it refreshed
                Toast.makeText(this, "Data Refreshed", Toast.LENGTH_SHORT).show();
            }
        });

        //Setup Add Expense Button
        LinearLayout btnAddExpense = findViewById(R.id.btnAddExpense);
        btnAddExpense.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, AddExpenseActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("IS_EDIT_MODE", false);
            intent.putExtra("TRIP_MEMBERS", this.membersRaw);
            startActivity(intent);
        });

        //Setup Add Payment Button
        LinearLayout btnAddPayment = findViewById(R.id.btnAddPayment);
        if (btnAddPayment != null) {
            btnAddPayment.setOnClickListener(v -> {
                Intent intent = new Intent(TripDetailsActivity.this, AddPaymentActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                intent.putExtra("TRIP_MEMBERS", this.membersRaw); // Always use the fresh global list
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

        //Setup Edit Trip Button
        LinearLayout btnEditTrip = findViewById(R.id.btnEditTrip);
        btnEditTrip.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, UpdateTripActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_NAME", ((TextView) findViewById(R.id.txt_details_trip_name)).getText().toString());
            intent.putExtra("TRIP_DESTINATION", ((TextView) findViewById(R.id.txt_details_destination)).getText().toString());

            // Force the use of the global variables with 'this.'
            intent.putExtra("TRIP_START_DATE", this.startDateFromDatabase);
            intent.putExtra("TRIP_END_DATE", this.endDateFromDatabase);
            intent.putExtra("TRIP_MEMBERS", this.membersRaw);

            editTripLauncher.launch(intent);
        });


        // UI Binding
        if (name != null) {
            ((TextView) findViewById(R.id.txt_details_trip_name)).setText(String.format(Locale.US, "%s", name));
        }
        ((TextView) findViewById(R.id.txt_details_destination)).setText(dest != null ? dest : "N/A");
        ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(date));

        // Setup Complete Ledger Button
        Button btnCompleteLedger = findViewById(R.id.btn_complete_ledger);
        btnCompleteLedger.setOnClickListener(v -> {
            Intent intent = new Intent(TripDetailsActivity.this, CompleteLedgerActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            startActivity(intent);
        });

        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
            // Initial load
            refreshSummaryCards(db);

            // 1. Process Active Members
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

            // 2. Process Inactive/Removed Members
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

    // --- NEW: Refreshes numbers every time user returns to this screen ---
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
                // 1. Fetch Basic Info
                String name = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_NAME));
                String dest = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_DESTINATION));
                String sDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_START_DATE));
                String eDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_END_DATE));
                String currentMembersRaw = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBERS));

                // 2. Update UI Fields
                ((TextView) findViewById(R.id.txt_details_trip_name)).setText(name);
                ((TextView) findViewById(R.id.txt_details_destination)).setText(dest);
                ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(sDate));

                // Update class-level variables
                startDateFromDatabase = sDate;
                endDateFromDatabase = eDate;

                // 3. Refresh Active Members
                ArrayList<String> activeMembers = new ArrayList<>();
                if (currentMembersRaw != null && !currentMembersRaw.isEmpty()) {
                    for (String m : currentMembersRaw.split(",")) {
                        if (!m.trim().isEmpty()) activeMembers.add(m.trim());
                    }
                }

                // Update Member Count
                ((TextView) findViewById(R.id.txt_details_member_count)).setText(String.valueOf(activeMembers.size()));

                // Refresh Active Grid
                GridLayout gridActive = findViewById(R.id.grid_members);
                gridActive.removeAllViews();
                for (String activeName : activeMembers) {
                    addMemberButton(activeName, gridActive, tripId, true);
                }

                // 4. Refresh Inactive Members
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