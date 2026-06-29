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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class TripDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        String tripId = getIntent().getStringExtra("TRIP_ID");
        String name = getIntent().getStringExtra("TRIP_NAME");
        String dest = getIntent().getStringExtra("DESTINATION");
        String date = getIntent().getStringExtra("START_DATE");
        String membersRaw = getIntent().getStringExtra("MEMBERS");

        // UI Binding - Verify IDs exist in your XML
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
            // Populate Summary Cards
            ((TextView) findViewById(R.id.txt_details_fund_balance)).setText(String.format(Locale.US, "₹%.2f", db.getFundBalance(tripId)));
            ((TextView) findViewById(R.id.txt_details_total_expenses)).setText(String.format(Locale.US, "₹%.2f", db.getTripTotalExpenses(tripId)));
            ((TextView) findViewById(R.id.txt_details_total_receipts)).setText(String.format(Locale.US, "₹%.2f", db.getTripTotalPaymentsReceived(tripId)));

            // === 1. Active Members Logic ===
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

            // === 2. Inactive/Removed Members Logic ===
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

        // 1. Find anyone who paid for an expense or made a payment to the fund
        String query = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ? " +
                "UNION SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";
        try (Cursor c = db.getReadableDatabase().rawQuery(query, new String[]{tripId, tripId})) {
            while (c.moveToNext()) {
                String name = c.getString(0).trim();
                if (!"Fund".equalsIgnoreCase(name) && !members.contains(name)) members.add(name);
            }
        }

        // 2. NEW: Find anyone who consumed an expense (shared with)
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
}