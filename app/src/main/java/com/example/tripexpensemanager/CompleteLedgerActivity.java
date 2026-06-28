package com.example.tripexpensemanager;

import android.database.Cursor;
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
            // 1. Get ALL historical members (Active + Removed)
            ArrayList<String> allMembers = getAllHistoricalMembers(db, tripId);

            // 2. Build Header dynamically with separate Paid/Used columns
            buildHeaderRow(tableLayout, allMembers);

            // 3. Get initial fund balance for chronological calculation
            double currentBalance = db.getFundBalance(tripId);

            // 4. Fetch all transactions, newest first, including the actual transaction date
            String query = "SELECT transaction_date, purpose, amount, paid_by, type, expense_shared_with FROM (" +
                    "SELECT expense_date as transaction_date, expense_purpose as purpose, expense_amount as amount, expense_paid_by as paid_by, expense_shared_with, 'Expense' as type, created_at FROM expenses WHERE expense_trip_id = ? " +
                    "UNION ALL " +
                    "SELECT payment_date as transaction_date, 'Payment' as purpose, payment_amount as amount, payment_by as paid_by, '' as expense_shared_with, 'Payment' as type, created_at FROM payments WHERE payment_trip_id = ? " +
                    ") ORDER BY created_at DESC";

            try (Cursor cursor = db.getReadableDatabase().rawQuery(query, new String[]{tripId, tripId})) {
                while (cursor.moveToNext()) {
                    buildDataRow(tableLayout, cursor, allMembers, currentBalance);

                    // Reverse calculate the balance for the row above
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                    String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));

                    if ("Payment".equals(type)) {
                        currentBalance -= amount;
                    } else if ("Fund".equals(paidBy)) {
                        currentBalance += amount;
                    }
                }
            }
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

    private void buildDataRow(TableLayout table, Cursor cursor, ArrayList<String> members, double balance) {
        TableRow row = new TableRow(this);

        String date = cursor.getString(cursor.getColumnIndexOrThrow("transaction_date"));
        String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
        double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
        String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
        String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

        // Replace the "Entry" placeholder with the actual transaction date
        addCell(row, (date != null ? date : "N/A"), false);
        addCell(row, purpose, false);
        addCell(row, String.format(Locale.US, "%.1f", amount), false);

        for (String m : members) {
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

            // Populate the two separate cells (Paid and Used)
            addCell(row, String.format(Locale.US, "%.1f", paidVal), false);
            addCell(row, String.format(Locale.US, "%.1f", usedVal), false);
        }

        addCell(row, String.format(Locale.US, "%.1f", balance), false);
        table.addView(row);
    }

    private ArrayList<String> getAllHistoricalMembers(TripDatabaseHelper db, String tripId) {
        ArrayList<String> members = new ArrayList<>();
        // Query both expenses and payments to catch any member who ever participated
        String query = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ? " +
                "UNION SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";
        try (Cursor c = db.getReadableDatabase().rawQuery(query, new String[]{tripId, tripId})) {
            while (c.moveToNext()) {
                String name = c.getString(0);
                if (!"Fund".equals(name) && !members.contains(name)) {
                    members.add(name);
                }
            }
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
        }
        row.addView(tv);
    }
}