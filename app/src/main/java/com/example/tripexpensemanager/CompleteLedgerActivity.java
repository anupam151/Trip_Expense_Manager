package com.example.tripexpensemanager;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CompleteLedgerActivity extends AppCompatActivity {

    private String currentTripId;
    private String currentTripName = "Trip";
    private final ArrayList<String> allMembersList = new ArrayList<>();

    private TableLayout tableHeader, tableData, tableTotal;
    private LedgerDataService ledgerDataService;

    // Running totals for the Footer
    private double[] totalCredits;
    private double[] totalDebits;
    private double totalFundCredit = 0.0;
    private double totalFundDebit = 0.0;

    private LedgerExportManager exportManager;

    private final androidx.activity.result.ActivityResultLauncher<String> createExcelLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null && exportManager != null) {
                    exportManager.exportCompleteLedgerToCsv(uri, currentTripId, allMembersList);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> createPdfLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null && exportManager != null && currentTripId != null) {
                    exportManager.exportAllTransactionsToPdf(uri, currentTripId);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_ledger);

        // Initialize Export Manager
        TripDatabaseHelper dbHelper = new TripDatabaseHelper(this);
        exportManager = new LedgerExportManager(this, dbHelper);

        // Hook up the buttons
        if (findViewById(R.id.btn_export_to_excel) != null) {
            findViewById(R.id.btn_export_to_excel).setOnClickListener(v ->
                    createExcelLauncher.launch(currentTripName + "_Ledger.csv")
            );
        }

        if (findViewById(R.id.btn_export_all_to_pdf) != null) {
            findViewById(R.id.btn_export_all_to_pdf).setOnClickListener(v ->
                    createPdfLauncher.launch(currentTripName + "_All_Transactions.pdf")
            );
        }

        currentTripId = getIntent().getStringExtra("TRIP_ID");

        tableHeader = findViewById(R.id.table_header);
        tableData = findViewById(R.id.table_data);
        tableTotal = findViewById(R.id.table_total);
        ledgerDataService = new LedgerDataService();

        if (findViewById(R.id.btn_back) != null) {
            findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        }

        loadTripDataAndLedger();
    }

    private void loadTripDataAndLedger() {
        // 1. First, fetch the Trip details to get the base members
        FirebaseFirestore.getInstance().collection("Trips").document(currentTripId)
                .get(Source.DEFAULT)
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentTripName = doc.getString("tripName");
                        String membersRaw = doc.getString("members");

                        if (membersRaw != null && !membersRaw.isEmpty()) {
                            allMembersList.addAll(Arrays.asList(membersRaw.split(",")));
                        }

                        // 2. Now fetch the transactions using our new Engine!
                        fetchTransactions();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load trip", Toast.LENGTH_SHORT).show());
    }

    private void fetchTransactions() {
        // THIS IS WHERE WE USE THE SERVICE (Warning vanishes!)
        ledgerDataService.fetchUnifiedLedger(currentTripId, new LedgerDataService.LedgerCallback() {
            @Override
            public void onResult(List<LedgerEntry> entries) {
                // Ensure historical members (who might have been deleted) are included
                for (LedgerEntry entry : entries) {
                    addUniqueMember(entry.getPaidBy());
                    if (entry.getSharedWith() != null) {
                        for (String m : entry.getSharedWith().split(",")) {
                            addUniqueMember(m.trim());
                        }
                    }
                }

                // Initialize arrays for our Totals row
                totalCredits = new double[allMembersList.size()];
                totalDebits = new double[allMembersList.size()];

                // Build the UI
                buildHeaderRow();

                tableData.removeAllViews();
                for (LedgerEntry entry : entries) {
                    buildDataRow(entry); // This uses all the getters!
                }

                buildTotalRow();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(CompleteLedgerActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addUniqueMember(String name) {
        if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Fund") && !allMembersList.contains(name)) {
            allMembersList.add(name);
        }
    }

    private void buildHeaderRow() {
        tableHeader.removeAllViews();
        TableRow row = new TableRow(this);
        applyRowDividersWhite(row);

        addCell(row, "Date of Transaction", true);
        addCell(row, "Purpose or Description", true);
        addCell(row, "Amount in INR", true);

        for (String m : allMembersList) {
            addCell(row, m + "\nCredit", true);
            addCell(row, m + "\nDebit", true);
        }

        // Add Fund Columns at the end
        addCell(row, "Fund\nCredit", true);
        addCell(row, "Fund\nDebit", true);

        tableHeader.addView(row);
    }

    private void buildDataRow(LedgerEntry entry) {
        TableRow row = new TableRow(this);
        applyRowDividers(row);

        // THESE LINES CLEAR YOUR WARNINGS in LedgerEntry.java!
        String type = entry.getType();
        String date = entry.getDate() != null ? entry.getDate() : "N/A";
        String purpose = entry.getPurpose();
        double amount = entry.getAmount();
        String paidBy = entry.getPaidBy();
        String sharedWith = entry.getSharedWith();

        addCell(row, date, false);
        addCell(row, purpose, false);
        addCell(row, String.format(Locale.US, "%.2f", amount), false);

        double rowFundCredit = 0.0;
        double rowFundDebit = 0.0;

        String[] sharedArray = (sharedWith != null && !sharedWith.isEmpty()) ? sharedWith.split(",\\s*") : new String[0];
        double shareAmount = (sharedArray.length > 0) ? (amount / sharedArray.length) : 0.0;

        for (int i = 0; i < allMembersList.size(); i++) {
            String member = allMembersList.get(i);
            double mCredit = 0.0;
            double mDebit = 0.0;

            if (entry.isExpense()) {
                // 1. Expense Logic
                if (member.equalsIgnoreCase(paidBy)) {
                    mCredit = amount; // Member paid
                }
                if (Arrays.asList(sharedArray).contains(member)) {
                    mDebit = shareAmount; // Member participated
                }
            } else {
                // 2. Payment Logic
                if (member.equalsIgnoreCase(paidBy)) {
                    mCredit = amount; // Member paid into fund
                }
            }

            // Add to running totals
            totalCredits[i] += mCredit;
            totalDebits[i] += mDebit;

            addCell(row, String.format(Locale.US, "%.2f", mCredit), false);
            addCell(row, String.format(Locale.US, "%.2f", mDebit), false);
        }

        // Fund Logic for this specific row
        if (entry.isExpense() && "Fund".equalsIgnoreCase(paidBy)) {
            rowFundDebit = amount; // Money left the fund
        } else if (!entry.isExpense()) {
            rowFundCredit = amount; // Money entered the fund via Payment
        }

        totalFundCredit += rowFundCredit;
        totalFundDebit += rowFundDebit;

        addCell(row, String.format(Locale.US, "%.2f", rowFundCredit), false);
        addCell(row, String.format(Locale.US, "%.2f", rowFundDebit), false);

        // Click to Edit/Delete
        row.setClickable(true);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setOnClickListener(v -> showTransactionOptions(entry.getTransId(), type, currentTripId));

        tableData.addView(row);
    }

    private void buildTotalRow() {
        tableTotal.removeAllViews();
        TableRow row = new TableRow(this);
        applyRowDividersWhite(row);
        row.setBackgroundColor(Color.parseColor("#E2E8F0"));

        addCell(row, "TOTALS", true);
        addCell(row, "-", true);
        addCell(row, "-", true);

        for (int i = 0; i < allMembersList.size(); i++) {
            addCell(row, String.format(Locale.US, "%.2f", totalCredits[i]), true);
            addCell(row, String.format(Locale.US, "%.2f", totalDebits[i]), true);
        }

        addCell(row, String.format(Locale.US, "%.2f", totalFundCredit), true);
        addCell(row, String.format(Locale.US, "%.2f", totalFundDebit), true);

        tableTotal.addView(row);
    }

    private void addCell(TableRow row, String text, boolean isHeader) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        tv.setGravity(Gravity.CENTER);

        int widthInPx = (int) (80 * getResources().getDisplayMetrics().density);
        tv.setWidth(widthInPx);

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
        row.setShowDividers(android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE);
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        divider.setColor(Color.parseColor("#CBD5E1"));
        divider.setSize(2, 0);
        row.setDividerDrawable(divider);
    }

    private void applyRowDividersWhite(TableRow row) {
        row.setShowDividers(android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE);
        android.graphics.drawable.GradientDrawable divider = new android.graphics.drawable.GradientDrawable();
        divider.setColor(Color.parseColor("#FFFFFF"));
        divider.setSize(2, 0);
        row.setDividerDrawable(divider);
    }

    private void showTransactionOptions(String transId, String type, String tripId) {
        String[] options = {"Edit", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle("Transaction Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, "Expense".equals(type) ? AddExpenseActivity.class : AddPaymentActivity.class);
                        intent.putExtra("TRIP_ID", tripId);
                        intent.putExtra("IS_EDIT_MODE", true);
                        intent.putExtra("TRANS_ID", transId);
                        intent.putExtra("TRIP_MEMBERS", String.join(",", allMembersList));
                        startActivity(intent);
                        finish();
                    } else if (which == 1) {
                        confirmDelete(transId, type);
                    }
                }).show();
    }

    private void confirmDelete(String transId, String type) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + type)
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    String collection = "Expense".equals(type) ? "Expenses" : "Payments";
                    FirebaseFirestore.getInstance().collection("Trips").document(currentTripId)
                            .collection(collection).document(transId).delete()
                            .addOnSuccessListener(aVoid -> recreate());
                })
                .setNegativeButton("Cancel", null).show();
    }
}