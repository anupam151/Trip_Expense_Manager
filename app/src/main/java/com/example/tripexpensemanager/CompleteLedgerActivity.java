package com.example.tripexpensemanager;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Locale;

public class CompleteLedgerActivity extends AppCompatActivity {

    // --- NEW: Export Manager and Global Variables ---
    private LedgerExportManager exportManager;
    private ArrayList<String> allMembersList;
    private String currentTripId;
    private String currentTripName = "Trip";

    // --- NEW: The SAF Launcher that opens the "Save As" screen safely ---
    private final ActivityResultLauncher<String> createExcelLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null && exportManager != null) {
                    exportManager.exportCompleteLedgerToCsv(uri, currentTripId, allMembersList);
                }
            });

    @SuppressWarnings("unused")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_ledger);

        // Assign to our global variable instead of a local one
        currentTripId = getIntent().getStringExtra("TRIP_ID");

        // --- UPDATED: Map the 3 separate tables from the new XML ---
        TableLayout tableHeader = findViewById(R.id.table_header);
        TableLayout tableData = findViewById(R.id.table_data);
        TableLayout tableTotal = findViewById(R.id.table_total);


        if (findViewById(R.id.btn_back) != null) {
            findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        }

        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
            // --- NEW: Initialize the Export Engine ---
            exportManager = new LedgerExportManager(this, db);
            allMembersList = getAllHistoricalMembers(db, currentTripId);
            currentTripName = fetchTripName(db, currentTripId);

            double[] totalPaid = new double[allMembersList.size()];
            double[] totalUsed = new double[allMembersList.size()];

            // --- UPDATED: Pass the Header Table ---
            buildHeaderRow(tableHeader, allMembersList);

            double currentBalance = 0.0;
            try (Cursor cursor = db.getUnifiedLedger(currentTripId)) {
                while (cursor.moveToNext()) {
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                    double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                    String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));

                    if ("Payment".equals(type)) {
                        currentBalance += amount;
                    } else if ("Expense".equals(type) && "Fund".equals(paidBy)) {
                        currentBalance -= amount;
                    }

                    // --- UPDATED: Pass the scrollable Data Table ---
                    buildDataRow(tableData, cursor, allMembersList, totalPaid, totalUsed);
                }
            }

            // --- UPDATED: Pass the Footer (Total) Table ---
            buildTotalRow(tableTotal, allMembersList, totalPaid, totalUsed);
        }

        // --- NEW: Hook up the Export to Excel Button ---
        if (findViewById(R.id.btn_export_to_excel) != null) {
            findViewById(R.id.btn_export_to_excel).setOnClickListener(v ->
                    createExcelLauncher.launch(currentTripName + "_Ledger.csv")
            );
        }
    }

    private void buildHeaderRow(TableLayout table, ArrayList<String> members) {
        TableRow row = new TableRow(this);
        applyRowDividersWhite(row);
        addCell(row, "Date of \nTransaction", true);
        addCell(row, "Purpose or \nDescription", true);
        addCell(row, "Total \nAmount", true);

        for (String m : members) {
            addCell(row, m + "\nCredit", true);
            addCell(row, m + "\nDebit", true);
        }

        // addCell(row, "Fund", true);
        table.addView(row);
    }

    private void buildDataRow(TableLayout table, Cursor cursor, ArrayList<String> members, double[] totalPaid, double[] totalUsed) {
        TableRow row = new TableRow(this);
        applyRowDividers(row);

        // --- NEW: Grab the hidden Transaction ID ---
        int transId = cursor.getInt(cursor.getColumnIndexOrThrow("trans_id"));

        String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
        String purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose"));
        double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
        String paidBy = cursor.getString(cursor.getColumnIndexOrThrow("paid_by"));
        String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
        String sharedWith = cursor.getString(cursor.getColumnIndexOrThrow("expense_shared_with"));
        String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",") : new String[0];

        addCell(row, (date != null ? date : "N/A"), false);
        addCell(row, purpose, false);
        addCell(row, String.format(Locale.US, "%.2f", amount), false);

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

            totalPaid[i] += paidVal;
            totalUsed[i] += usedVal;

            addCell(row, String.format(Locale.US, "%.2f", paidVal), false);
            addCell(row, String.format(Locale.US, "%.2f", usedVal), false);
        }

        // --- NEW: Make Row Clickable for Edit/Delete ---
        row.setClickable(true);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setOnClickListener(v -> showTransactionOptions(transId, type, currentTripId)); // Used global variable here

        table.addView(row);
    }

    // --- NEW EDIT & DELETE DIALOGS ---
    private void showTransactionOptions(int transId, String type, String tripId) {
        String[] options = {"Edit", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle("Transaction Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // EDIT
                        Intent intent;
                        if ("Expense".equals(type)) {
                            intent = new Intent(this, AddExpenseActivity.class);
                        } else {
                            intent = new Intent(this, AddPaymentActivity.class);
                        }
                        intent.putExtra("TRIP_ID", tripId);
                        intent.putExtra("IS_EDIT_MODE", true);
                        intent.putExtra("TRANS_ID", transId);

                        // Fetch existing members string from DB to pass to the Edit Activity
                        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
                            intent.putExtra("TRIP_MEMBERS", getMembersForTrip(db, tripId));
                        }
                        startActivity(intent);
                        finish(); // Close Ledger so it reloads fresh data when coming back
                    } else if (which == 1) {
                        // DELETE
                        confirmDelete(transId, type);
                    }
                })
                .show();
    }

    private void confirmDelete(int transId, String type) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + type)
                .setMessage("Are you sure you want to delete this " + type + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
                        if ("Expense".equals(type)) {
                            db.deleteExpense(transId);
                        } else {
                            db.deletePayment(transId);
                        }
                    }
                    recreate(); // Instantly refresh the page
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void buildTotalRow(TableLayout table, ArrayList<String> members, double[] totalPaid, double[] totalUsed) {
        TableRow row = new TableRow(this);
        applyRowDividersWhite(row);
        row.setBackgroundColor(Color.parseColor("#E2E8F0"));

        addCell(row, "TOTALS", true);
        addCell(row, "-", true);
        addCell(row, "-", true);

        for (int i = 0; i < members.size(); i++) {
            addCell(row, String.format(Locale.US, "%.2f", totalPaid[i]), true);
            addCell(row, String.format(Locale.US, "%.2f", totalUsed[i]), true);
        }

        // addCell(row, "-", true);
        table.addView(row);
    }

    private ArrayList<String> getAllHistoricalMembers(TripDatabaseHelper db, String tripId) {
        ArrayList<String> members = new ArrayList<>();
        String baseMembersStr = getMembersForTrip(db, tripId);
        if (baseMembersStr != null && !baseMembersStr.isEmpty()) {
            String[] baseMembers = baseMembersStr.split(",");
            for (String m : baseMembers) {
                String cleanName = m.trim();
                if (!cleanName.isEmpty() && !members.contains(cleanName)) members.add(cleanName);
            }
        }

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
                    String[] sharedArray = sharedStr.split(",");
                    for (String s : sharedArray) {
                        String cleanName = s.trim();
                        if (!cleanName.isEmpty() && !members.contains(cleanName)) members.add(cleanName);
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

        // --- CRITICAL FIX: Force uniform width (80dp) so the 3 tables align horizontally ---
        int widthInPx = (int) (80 * getResources().getDisplayMetrics().density);
        tv.setWidth(widthInPx);
        // ------------------------------------------------------------------------------------

        if (isHeader) {
            tv.setBackgroundColor(Color.parseColor("#85022E"));
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            tv.setTextColor(Color.parseColor("#2D3748"));
        }
        row.addView(tv);
    }

    private void applyRowDividers(TableRow row) {
        // This tells the row to draw a line between every cell
        row.setShowDividers(android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE);

        // Create the physical line (2 pixels wide, light gray color)
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        divider.setColor(Color.parseColor("#CBD5E1"));
        divider.setSize(2, 0);

        row.setDividerDrawable(divider);
    }
    //white vertical line for column divider
    private void applyRowDividersWhite(TableRow row) {
        // This tells the row to draw a line between every cell
        row.setShowDividers(android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE);
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        divider.setColor(Color.parseColor("#FFFFFF"));
        divider.setSize(2, 0);

        row.setDividerDrawable(divider);
    }
    // --- NEW: Fetch and clean the trip name for the file export ---
    private String fetchTripName(TripDatabaseHelper db, String tripId) {
        String name = "Trip";
        String query = "SELECT " + TripDatabaseHelper.COLUMN_TRIP_NAME +
                " FROM " + TripDatabaseHelper.TABLE_TRIPS +
                " WHERE " + TripDatabaseHelper.COLUMN_TRIP_ID + " = ?";
        try (Cursor cursor = db.getReadableDatabase().rawQuery(query, new String[]{tripId})) {
            if (cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception e) {
            // FIXED: Using Android's official Log system instead of printStackTrace
            android.util.Log.e("CompleteLedger", "Error fetching trip name", e);
        }
        // Replace spaces and special characters with underscores for safe file saving
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }
}