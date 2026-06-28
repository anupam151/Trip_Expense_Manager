package com.example.tripexpensemanager;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Locale;

public class CompleteLedgerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_ledger);

        String tripId = getIntent().getStringExtra("TRIP_ID");
        TableLayout tableLayout = findViewById(R.id.table_full_ledger);

        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
            // 1. Get ALL historical members (Base Members + Active + Removed)
            ArrayList<String> allMembers = getAllHistoricalMembers(db, tripId);

            // 2. Arrays to hold the sum of Paid and Used for each member
            double[] totalPaid = new double[allMembers.size()];
            double[] totalUsed = new double[allMembers.size()];

            // 3. Build Header dynamically with separate Paid/Used columns
            buildHeaderRow(tableLayout, allMembers);

            // 4. Initial balance is 0 because we are starting from the very first (oldest) transaction
            double currentBalance = 0.0;

            // 5. Fetch all transactions (Sorted automatically by device entry time via DB Helper)
            try (Cursor cursor = db.getUnifiedLedger(tripId)) {
                while (cursor.moveToNext()) {
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                    String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));

                    // Forward calculate the fund balance correctly
                    if ("Payment".equals(type)) {
                        currentBalance += amount;
                    } else if ("Expense".equals(type) && "Fund".equals(paidBy)) {
                        currentBalance -= amount;
                    }

                    buildDataRow(tableLayout, cursor, allMembers, currentBalance, totalPaid, totalUsed);
                }
            }

            // 6. Append the final Totals row at the bottom
            buildTotalRow(tableLayout, allMembers, totalPaid, totalUsed);
        }
    }

    private void buildHeaderRow(TableLayout table, ArrayList<String> members) {
        TableRow row = new TableRow(this);
        addCell(row, "Date", true);
        addCell(row, "Purpose", true);
        addCell(row, "Amount", true);

        // Generate two columns for each member
        for (String m : members) {
            addCell(row, m + "\nPaid", true);
            addCell(row, m + "\nUsed", true);
        }

        addCell(row, "Fund", true);
        table.addView(row);
    }

    private void buildDataRow(TableLayout table, Cursor cursor, ArrayList<String> members, double balance, double[] totalPaid, double[] totalUsed) {
        TableRow row = new TableRow(this);

        // Date shown here is the user input date from the form!
        String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
        String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
        double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
        String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
        String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

        addCell(row, (date != null ? date : "N/A"), false);
        addCell(row, purpose, false);
        addCell(row, String.format(Locale.US, "%.1f", amount), false);

        for (int i = 0; i < members.size(); i++) {
            String m = members.get(i);
            double paidVal = 0.0;
            double usedVal = 0.0;

            if ("Expense".equals(type)) {
                double share = (sharedArray.length > 0) ? (amount / sharedArray.length) : 0.0;
                if (m.equals(paidBy)) {
                    paidVal = amount;
                }
                if (isParticipant(m, sharedArray)) {
                    usedVal = share;
                }
            } else if ("Payment".equals(type) && m.equals(paidBy)) {
                paidVal = amount;
            }

            // Add to the running totals
            totalPaid[i] += paidVal;
            totalUsed[i] += usedVal;

            addCell(row, String.format(Locale.US, "%.1f", paidVal), false);
            addCell(row, String.format(Locale.US, "%.1f", usedVal), false);
        }

        addCell(row, String.format(Locale.US, "%.1f", balance), false);
        table.addView(row);
    }

    private void buildTotalRow(TableLayout table, ArrayList<String> members, double[] totalPaid, double[] totalUsed) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(Color.parseColor("#E2E8F0"));

        addCell(row, "TOTALS", true);
        addCell(row, "-", true);
        addCell(row, "-", true);

        for (int i = 0; i < members.size(); i++) {
            addCell(row, String.format(Locale.US, "%.1f", totalPaid[i]), true);
            addCell(row, String.format(Locale.US, "%.1f", totalUsed[i]), true);
        }

        addCell(row, "-", true);
        table.addView(row);
    }

    private ArrayList<String> getAllHistoricalMembers(TripDatabaseHelper db, String tripId) {
        ArrayList<String> members = new ArrayList<>();

        // 1. Pull the base members who were explicitly added to the trip
        String baseMembersStr = getMembersForTrip(db, tripId);
        if (baseMembersStr != null && !baseMembersStr.isEmpty()) {
            String[] baseMembers = baseMembersStr.split(",");
            for (String m : baseMembers) {
                String cleanName = m.trim();
                if (!cleanName.isEmpty() && !members.contains(cleanName)) {
                    members.add(cleanName);
                }
            }
        }

        // 2. Query expenses and payments to catch any member who ever participated historically
        String query = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ? " +
                "UNION SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";
        try (Cursor c = db.getReadableDatabase().rawQuery(query, new String[]{tripId, tripId})) {
            while (c.moveToNext()) {
                String name = c.getString(0).trim();
                if (!"Fund".equalsIgnoreCase(name) && !members.contains(name)) {
                    members.add(name);
                }
            }
        }

        // 3. Query the shared_with column to catch members who only consumed
        String sharedQuery = "SELECT expense_shared_with FROM expenses WHERE expense_trip_id = ?";
        try (Cursor c = db.getReadableDatabase().rawQuery(sharedQuery, new String[]{tripId})) {
            while (c.moveToNext()) {
                String sharedStr = c.getString(0);
                if (sharedStr != null && !sharedStr.isEmpty()) {
                    String[] sharedArray = sharedStr.split(",");
                    for (String s : sharedArray) {
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

    private String getMembersForTrip(TripDatabaseHelper db, String tripId) {
        SQLiteDatabase readableDb = db.getReadableDatabase();
        String members = "";
        String query = "SELECT " + TripDatabaseHelper.COLUMN_MEMBERS +
                " FROM " + TripDatabaseHelper.TABLE_TRIPS +
                " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?";
        try (Cursor cursor = readableDb.rawQuery(query, new String[]{tripId})) {
            if (cursor.moveToFirst()) members = cursor.getString(0);
        }
        return members;
    }

    private boolean isParticipant(String memberName, String[] sharedArray) {
        for (String s : sharedArray) {
            if (s.trim().equalsIgnoreCase(memberName.trim())) {
                return true;
            }
        }
        return false;
    }

    private void addCell(TableRow row, String text, boolean isHeader) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        tv.setGravity(Gravity.CENTER);
        if (isHeader) {
            tv.setBackgroundColor(Color.LTGRAY);
            tv.setTextColor(Color.BLACK);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            tv.setTextColor(Color.parseColor("#2D3748"));
        }
        row.addView(tv);
    }
}