package com.example.tripexpensemanager;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class TripLedgerActivity extends AppCompatActivity {

    private static final String TAG = "TripLedgerActivity";

    private TextView txtTripName, txtDestination, txtMemberCount, txtAllMembers;
    private TextView txtFundBalance, txtTotalExpenses, txtTotalPayments;
    private TableLayout tableLedger;

    private TripDatabaseHelper dbHelper;
    private String currentTripId;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    // Dynamic operational class structure to hold both data sets for unified sorting
    private static class LedgerEntry {
        Date dateObject;
        String dateString;
        String description;
        double expenseAmount;
        double paymentAmount;
        String expenseFor;
        String actionBy;

        // Getter added to seamlessly support standard Comparator methods
        public Date getDateObject() {
            return dateObject;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_ledger);

        // Binding UI Views
        txtTripName = findViewById(R.id.txt_ledger_trip_name);
        txtDestination = findViewById(R.id.txt_ledger_destination);
        txtMemberCount = findViewById(R.id.txt_ledger_member_count);
        txtAllMembers = findViewById(R.id.txt_ledger_all_members);

        txtFundBalance = findViewById(R.id.txt_ledger_fund_balance);
        txtTotalExpenses = findViewById(R.id.txt_ledger_total_expenses);
        txtTotalPayments = findViewById(R.id.txt_ledger_total_payments);
        tableLedger = findViewById(R.id.table_trip_ledger);

        dbHelper = new TripDatabaseHelper(this);

        if (getIntent() != null) {
            currentTripId = getIntent().getStringExtra("TRIP_ID");
            loadTripMetadataHeader();
            generateAndPopulateLedgerTable();
        } else {
            Toast.makeText(this, "Error: Missing data packet context!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadTripMetadataHeader() {
        try (Cursor cursor = dbHelper.getAllTripsCursor()) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String tripId = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_ID));
                    if (tripId.equals(currentTripId)) {
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_NAME));
                        String dest = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_DESTINATION));
                        String members = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBERS));
                        int count = cursor.getInt(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBER_COUNT));

                        // FIXED: Replaced concatenation text blocks with localized string resources
                        txtTripName.setText(getString(R.string.fmt_ledger_trip_name, name));
                        txtDestination.setText(getString(R.string.fmt_ledger_destination, dest));
                        txtMemberCount.setText(getString(R.string.fmt_ledger_member_count, count));
                        txtAllMembers.setText(getString(R.string.fmt_ledger_all_members, members));
                        break;
                    }
                }
            }
        }
    }

    private void generateAndPopulateLedgerTable() {
        ArrayList<LedgerEntry> unifiedLedgerList = new ArrayList<>();
        double accumulatedExpensesTotal = 0.0;
        double accumulatedPaymentsTotal = 0.0;

        // 1. Fetch data rows from TABLE_EXPENSES
        try (Cursor expCursor = dbHelper.getExpensesForTripCursor(currentTripId)) {
            if (expCursor != null) {
                while (expCursor.moveToNext()) {
                    LedgerEntry entry = new LedgerEntry();
                    entry.description = expCursor.getString(expCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_EXPENSE_PURPOSE));
                    entry.expenseAmount = expCursor.getDouble(expCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_EXPENSE_AMOUNT));
                    entry.paymentAmount = 0.0;

                    String sharedWith = expCursor.getString(expCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_EXPENSE_SHARED_WITH));
                    if (sharedWith != null) {
                        entry.expenseFor = sharedWith.replace(", ", "/").replace(",", "/");
                    } else {
                        entry.expenseFor = "NA";
                    }

                    entry.actionBy = expCursor.getString(expCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_EXPENSE_PAID_BY));

                    String dateStr = expCursor.getString(expCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_EXPENSE_DATE));
                    entry.dateString = (dateStr != null && !dateStr.trim().isEmpty()) ? dateStr.trim() : "01/01/2026";
                    entry.dateObject = parseDateSafely(entry.dateString);

                    accumulatedExpensesTotal += entry.expenseAmount;
                    unifiedLedgerList.add(entry);
                }
            }
        }

        // 2. Fetch data rows from TABLE_PAYMENTS
        try (Cursor payCursor = dbHelper.getPaymentsForTripCursor(currentTripId)) {
            if (payCursor != null) {
                while (payCursor.moveToNext()) {
                    LedgerEntry entry = new LedgerEntry();
                    entry.description = "Payment";
                    entry.expenseAmount = 0.0;
                    entry.paymentAmount = payCursor.getDouble(payCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_PAYMENT_AMOUNT));
                    entry.expenseFor = "NA";
                    entry.actionBy = payCursor.getString(payCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_PAYMENT_BY));

                    entry.dateString = payCursor.getString(payCursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_PAYMENT_DATE));
                    entry.dateObject = parseDateSafely(entry.dateString);

                    accumulatedPaymentsTotal += entry.paymentAmount;
                    unifiedLedgerList.add(entry);
                }
            }
        }

        // FIXED: Swapped Collections.sort with modern List.sort and introduced Comparator.comparing framework
        unifiedLedgerList.sort(Comparator.comparing(LedgerEntry::getDateObject));

        // FIXED: Applied clean formatted local resource mappings for global stats metrics variables text displays
        txtTotalExpenses.setText(getString(R.string.fmt_ledger_total_expenses, accumulatedExpensesTotal));
        txtTotalPayments.setText(getString(R.string.fmt_ledger_total_payments, accumulatedPaymentsTotal));
        txtFundBalance.setText(getString(R.string.label_ledger_calculation_pending));

        // 3. Populate and inject row widgets into TableLayout
        int serialNoCounter = 1;
        int padding8 = dpToPx(8);
        int padding12 = dpToPx(12);

        for (LedgerEntry item : unifiedLedgerList) {
            TableRow row = new TableRow(this);
            row.setPadding(0, dpToPx(6), 0, dpToPx(6));

            if (serialNoCounter % 2 == 0) {
                row.setBackgroundColor(Color.parseColor("#F7FAFC"));
            } else {
                row.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }

            // Column 1: SL No
            row.addView(createCellTextView(String.valueOf(serialNoCounter), Gravity.CENTER, padding8));

            // Column 2: Description
            row.addView(createCellTextView(item.description, Gravity.START, padding12));

            // Column 3: Date of Transaction
            row.addView(createCellTextView(item.dateString, Gravity.CENTER, padding12));

            // Column 4: Expense Amount
            row.addView(createCellTextView(getString(R.string.fmt_currency_rupees, item.expenseAmount), Gravity.END, padding12));

            // Column 5: Payment Amount
            row.addView(createCellTextView(getString(R.string.fmt_currency_rupees, item.paymentAmount), Gravity.END, padding12));

            // Column 6: Expense For
            row.addView(createCellTextView(item.expenseFor, Gravity.START, padding12));

            // Column 7: Expense By / Payment By
            row.addView(createCellTextView(item.actionBy, Gravity.START, padding12));

            tableLedger.addView(row);
            serialNoCounter++;
        }
    }

    private TextView createCellTextView(String text, int gravity, int horizontalPadding) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(gravity);
        tv.setTextColor(Color.parseColor("#2D3748"));
        tv.setTextSize(14);
        tv.setPadding(horizontalPadding, dpToPx(4), horizontalPadding, dpToPx(4));
        return tv;
    }

    private Date parseDateSafely(String dateStr) {
        try {
            if (dateStr != null) {
                return dateFormatter.parse(dateStr.trim());
            }
        } catch (ParseException e) {
            Log.e(TAG, "Failed to parse date row: " + dateStr, e);
        }
        return new Date(0);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
}