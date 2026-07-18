package com.example.tripexpensemanager;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.widget.ImageButton;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;

public class MemberLedgerActivity extends BaseDrawerActivity {

    private String memberName, tripId;
    private final List<Transaction> transactionList = new ArrayList<>();
    // Export Manager
    private LedgerExportManager exportManager;

    // SAF Launcher for Individual PDF Export
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

        // --- Wire up the Universal Drawer ---
        setupUniversalDrawer(R.id.drawer_layout, R.id.navigation_view);

        // --- Handle the Back button to close the drawer gracefully ---
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });

        // --- Wire up the hamburger menu icon ---
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // HOME BUTTON
        LinearLayout btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, DashboardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        // Retrieve passed data
        memberName = getIntent().getStringExtra("MEMBER_NAME");
        tripId = getIntent().getStringExtra("TRIP_ID");

        // COMPLETE LEDGER BUTTON
        LinearLayout btnCompleteLedger = findViewById(R.id.unv_btn_complete_ledger);
        if (btnCompleteLedger != null) {
            btnCompleteLedger.setOnClickListener(v -> {
                Intent intent = new Intent(this, CompleteLedgerActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                startActivity(intent);
            });
        }

        // Initialize Export Engine
        exportManager = new LedgerExportManager(this, new TripDatabaseHelper(this));

        TextView txtName = findViewById(R.id.txt_ledger_member_name);
        txtName.setText(memberName);

        RecyclerView recyclerView = findViewById(R.id.recycler_transactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 🟢 NEW: Setup SwipeRefreshLayout
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#85022E"));
            swipeRefreshLayout.setOnRefreshListener(this::loadLedgerData);
        }

        // Load data using Firestore Engine initially
        loadLedgerData();

        // Hook up the Export to PDF Button
        if (findViewById(R.id.btn_export_to_pdf) != null) {
            findViewById(R.id.btn_export_to_pdf).setOnClickListener(v -> {
                String fileName = memberName.replaceAll("[^a-zA-Z0-9]", "_") + "_Ledger.pdf";
                createPdfLauncher.launch(fileName);
            });
        }

        // Hook up the Direct Share Button
        android.view.View btnShare = findViewById(R.id.btn_share);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> {
                if (exportManager != null) {
                    exportManager.shareIndividualMemberPdf(tripId, memberName);
                }
            });
        }
    }

    private void loadLedgerData() {
        new LedgerDataService().fetchUnifiedLedger(tripId, new LedgerDataService.LedgerCallback() {
            @Override
            public void onResult(List<LedgerEntry> entries) {
                transactionList.clear();
                double totalDebit = 0;
                double totalCredit = 0;
                int mockIdCounter = 1;

                for (LedgerEntry entry : entries) {
                    String[] sharedArray = (entry.getSharedWith() != null && !entry.getSharedWith().isEmpty()) ? entry.getSharedWith().split(",\\s*") : new String[0];
                    double debit = 0.0;
                    double credit = 0.0;
                    boolean involvesMember = false;

                    // Apply Double-Entry Rules
                    if (entry.isExpense()) {
                        if (memberName.equals(entry.getPaidBy())) {
                            credit = entry.getAmount();
                            involvesMember = true;
                        }
                        if (isParticipant(memberName, sharedArray)) {
                            debit = entry.getAmount() / sharedArray.length;
                            involvesMember = true;
                        }
                    } else if (memberName.equals(entry.getPaidBy())) {
                        credit = entry.getAmount();
                        involvesMember = true;
                    }

                    // If member is involved, add to list and calculate totals
                    if (involvesMember) {
                        totalDebit += debit;
                        totalCredit += credit;

                        String safeDate = (entry.getDate() != null) ? entry.getDate() : "N/A";
                        String safePurpose = (entry.getPurpose() != null) ? entry.getPurpose() : "Cash Settlement";

                        transactionList.add(new Transaction(
                                mockIdCounter++,
                                safeDate,
                                safeDate + " 00:00:00",
                                safePurpose,
                                debit,
                                credit
                        ));
                    }
                }

                final double finalTotalDebit = totalDebit;
                final double finalTotalCredit = totalCredit;

                // Update UI on Main Thread
                runOnUiThread(() -> {
                    updateUI(finalTotalDebit, finalTotalCredit);

                    // 🟢 NEW: Stop the refresh animation on success
                    androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipe_refresh_layout);
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MemberLedgerActivity.this, "Failed to load data: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    // 🟢 NEW: Stop the refresh animation on error
                    androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipe_refresh_layout);
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }
        });
    }

    private void updateUI(double totalDebit, double totalCredit) {
        ((TextView) findViewById(R.id.txt_total_expenses)).setText(String.format(Locale.US, "₹%.2f", totalDebit));
        ((TextView) findViewById(R.id.txt_total_payments)).setText(String.format(Locale.US, "₹%.2f", totalCredit));
        ((TextView) findViewById(R.id.txt_footer_total_debit)).setText(String.format(Locale.US, "₹%.2f", totalDebit));
        ((TextView) findViewById(R.id.txt_footer_total_credit)).setText(String.format(Locale.US, "₹%.2f", totalCredit));
        ((TextView) findViewById(R.id.txt_transaction_count)).setText(String.format(Locale.US, "%d Transactions", transactionList.size()));

        double balance = totalCredit - totalDebit;
        TextView txtBalance = findViewById(R.id.txt_ledger_balance);
        txtBalance.setText(String.format(Locale.US, "₹%.2f", Math.abs(balance)));
        txtBalance.setTextColor(balance < 0 ? Color.parseColor("#85022E") : Color.parseColor("#2E7D32"));

        RecyclerView recyclerView = findViewById(R.id.recycler_transactions);
        String currentUserRole = "Viewer";
        recyclerView.setAdapter(new LedgerAdapter(transactionList, currentUserRole));
    }

    private boolean isParticipant(String memberName, String[] sharedArray) {
        for (String s : sharedArray) {
            if (s.trim().equalsIgnoreCase(memberName.trim())) return true;
        }
        return false;
    }
}