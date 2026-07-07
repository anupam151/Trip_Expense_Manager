package com.example.tripexpensemanager;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.view.View;

public class MemberLedgerActivity extends AppCompatActivity {

    private String memberName, tripId;
    private TripDatabaseHelper dbHelper;
    private final List<Transaction> transactionList = new ArrayList<>();

    // --- NEW: Export Manager ---
    private LedgerExportManager exportManager;

    // --- NEW: The SAF Launcher for Individual Excel Export ---
    private final ActivityResultLauncher<String> createExcelLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null && exportManager != null) {
                    exportManager.exportIndividualMemberToCsv(uri, tripId, memberName);
                }
            });

    // --- NEW: The SAF Launcher for Individual PDF Export ---
    private final ActivityResultLauncher<String> createPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null && exportManager != null) {
                    exportManager.exportIndividualMemberToPdf(uri, tripId, memberName);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_ledger);

        // Retrieve passed data
        memberName = getIntent().getStringExtra("MEMBER_NAME");
        tripId = getIntent().getStringExtra("TRIP_ID");
        dbHelper = new TripDatabaseHelper(this);

        // --- NEW: Initialize the Export Engine ---
        exportManager = new LedgerExportManager(this, dbHelper);

        // Bind UI
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        TextView txtName = findViewById(R.id.txt_ledger_member_name);
        txtName.setText(memberName);

        RecyclerView recyclerView = findViewById(R.id.recycler_transactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadLedgerData();

        // --- NEW: Hook up the Export to Excel Button ---
        if (findViewById(R.id.btn_export_to_excel) != null) {
            findViewById(R.id.btn_export_to_excel).setOnClickListener(v -> {
                // Dynamically sets the default file name to match the member!
                String fileName = memberName + "_Ledger.csv";
                createExcelLauncher.launch(fileName);
            });
        }
        // --- NEW: Hook up the Export to PDF Button ---
        if (findViewById(R.id.btn_export_to_pdf) != null) {
            findViewById(R.id.btn_export_to_pdf).setOnClickListener(v -> {
                String fileName = memberName + "_Ledger.pdf";
                createPdfLauncher.launch(fileName);
            });
        }
        // --- HOOK UP THE DIRECT SHARE BUTTON ---
        View btnShare = findViewById(R.id.btn_share);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                if (exportManager != null) {
                    exportManager.shareIndividualMemberPdf(tripId, memberName);
                }
            });
        }
    }

    private void loadLedgerData() {
        transactionList.clear();
        double totalDebit = 0;
        double totalCredit = 0;

        // 1. Fetch Expenses - Using expense_id as unique ID
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT expense_id, expense_date, expense_purpose, expense_amount, expense_paid_by, expense_shared_with FROM expenses WHERE expense_trip_id = ?",
                new String[]{tripId})) {

            while (c.moveToNext()) {
                int id = c.getInt(0);
                String date = c.getString(1);
                String purpose = c.getString(2);
                double fullAmount = c.getDouble(3);
                String paidBy = c.getString(4);
                String sharedWith = c.getString(5);
                String timestamp = date + " 00:00:00"; // Fallback timestamp

                String[] members = sharedWith.split(",");
                double share = fullAmount / members.length;

                if (paidBy.equals(memberName) && sharedWith.contains(memberName)) {
                    transactionList.add(new Transaction(id, date, timestamp, purpose, share, fullAmount));
                    totalDebit += share; totalCredit += fullAmount;
                } else if (sharedWith.contains(memberName)) {
                    transactionList.add(new Transaction(id, date, timestamp, purpose, share, 0));
                    totalDebit += share;
                } else if (paidBy.equals(memberName)) {
                    transactionList.add(new Transaction(id, date, timestamp, purpose, 0, fullAmount));
                    totalCredit += fullAmount;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("LEDGER_ERROR", "Expense Query Failed: " + e.getMessage());
        }

        // 2. Fetch Payments - Using payment_id
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT payment_id, payment_date, payment_amount FROM payments WHERE payment_trip_id = ? AND payment_by = ?",
                new String[]{tripId, memberName})) {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                double amount = c.getDouble(2);
                String timestamp = c.getString(1) + " 00:00:00";
                transactionList.add(new Transaction(id, c.getString(1), timestamp, "Cash Settlement", 0, amount));
                totalCredit += amount;
            }
        } catch (Exception e) {
            android.util.Log.e("LEDGER_ERROR", "Payment Query Failed: " + e.getMessage());
        }

        // 3. Sort and Refresh
        sortTransactionsByTimestamp();

        // 4. Update UI
        ((TextView) findViewById(R.id.txt_total_expenses)).setText(String.format(Locale.US, "₹%.2f", totalDebit));
        ((TextView) findViewById(R.id.txt_total_payments)).setText(String.format(Locale.US, "₹%.2f", totalCredit));
        ((TextView) findViewById(R.id.txt_footer_total_debit)).setText(String.format(Locale.US, "₹%.2f", totalDebit));
        ((TextView) findViewById(R.id.txt_footer_total_credit)).setText(String.format(Locale.US, "₹%.2f", totalCredit));
        ((TextView) findViewById(R.id.txt_transaction_count)).setText(String.format(Locale.US, "%d Transactions", transactionList.size()));

        double balance = totalCredit - totalDebit;
        TextView txtBalance = findViewById(R.id.txt_ledger_balance);
        txtBalance.setText(String.format(Locale.US, "₹%.2f", Math.abs(balance)));
        txtBalance.setTextColor(balance < 0 ? Color.parseColor("#85022E") : Color.parseColor("#2E7D32"));

        // Final Adapter assignment
        RecyclerView recyclerView = findViewById(R.id.recycler_transactions);
        recyclerView.setAdapter(new LedgerAdapter(transactionList));
    }

    private void sortTransactionsByTimestamp() {
        // 1. Define the format to match your data (dd/MM/yyyy HH:mm:ss)
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);

        transactionList.sort((t1, t2) -> {
            try {
                // 2. Parse the timestamps
                java.util.Date date1 = sdf.parse(t1.timestamp);
                java.util.Date date2 = sdf.parse(t2.timestamp);

                if (date1 == null || date2 == null) return 0;

                // 3. Compare the dates
                int dateComparison = date1.compareTo(date2);

                // 4. If dates are different, return the date comparison
                if (dateComparison != 0) {
                    return dateComparison;
                } else {
                    // 5. TIE-BREAKER: If dates are identical, use the database ID.
                    // This ensures that items entered later always appear later.
                    return Integer.compare(t1.id, t2.id);
                }
            } catch (java.text.ParseException e) {
                // If parsing fails, don't change the order
                return 0;
            }
        });
    }
}