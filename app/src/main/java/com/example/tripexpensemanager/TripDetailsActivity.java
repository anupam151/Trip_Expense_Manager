package com.example.tripexpensemanager;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class TripDetailsActivity extends AppCompatActivity {

    private String tripId;
    private String startDateFromDatabase;
    private String endDateFromDatabase;
    private String membersRaw;
    private String tripName;

    private LedgerExportManager exportManager;
    private FirebaseFirestore db;

    private final ActivityResultLauncher<String> createMasterPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null && exportManager != null) {
                    ArrayList<String> membersList = new ArrayList<>();
                    if (membersRaw != null && !membersRaw.isEmpty()) {
                        membersList.addAll(Arrays.asList(membersRaw.split(",")));
                    }
                    exportManager.exportAllMembersToPdf(uri, tripId, membersList);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

        db = FirebaseFirestore.getInstance();
        TripDatabaseHelper dbHelper = new TripDatabaseHelper(this);
        exportManager = new LedgerExportManager(this, dbHelper);

        tripId = getIntent().getStringExtra("TRIP_ID");
        tripName = getIntent().getStringExtra("TRIP_NAME");
        String dest = getIntent().getStringExtra("DESTINATION");
        String date = getIntent().getStringExtra("START_DATE");
        startDateFromDatabase = getIntent().getStringExtra("START_DATE");
        endDateFromDatabase = getIntent().getStringExtra("END_DATE");
        membersRaw = getIntent().getStringExtra("MEMBERS");

        // UI Setup
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnAddExpense).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddExpenseActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_MEMBERS", this.membersRaw);
            startActivity(intent);
        });

        findViewById(R.id.btnAddPayment).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddPaymentActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_MEMBERS", this.membersRaw);
            startActivity(intent);
        });

        findViewById(R.id.btnEditTrip).setOnClickListener(v -> {
            Intent intent = new Intent(this, UpdateTripActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_NAME", tripName);
            intent.putExtra("TRIP_DESTINATION", ((TextView) findViewById(R.id.txt_details_destination)).getText().toString());
            intent.putExtra("TRIP_START_DATE", this.startDateFromDatabase);
            intent.putExtra("TRIP_END_DATE", this.endDateFromDatabase);
            intent.putExtra("TRIP_MEMBERS", this.membersRaw);
            startActivity(intent);
        });

        findViewById(R.id.btnDeleteTrip).setOnClickListener(v -> showDeleteDialog());
        findViewById(R.id.btn_share_all).setOnClickListener(v -> exportManager.shareMasterPdf(tripId));

        findViewById(R.id.btn_all_individual_to_one_pdf).setOnClickListener(v -> {
            String fileName = (tripName != null ? tripName.replaceAll("[^a-zA-Z0-9]", "_") : "Trip") + "_Master_Ledger.pdf";
            createMasterPdfLauncher.launch(fileName);
        });

        findViewById(R.id.btn_complete_ledger).setOnClickListener(v -> {
            Intent intent = new Intent(this, CompleteLedgerActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            startActivity(intent);
        });

        ((TextView) findViewById(R.id.txt_details_trip_name)).setText(getString(R.string.format_trip_name_header, tripName));
        ((TextView) findViewById(R.id.txt_details_destination)).setText(dest != null ? dest : "N/A");
        ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(date));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTripDetails();
    }

    private void refreshTripDetails() {
        db.collection("Trips").document(tripId).get(Source.DEFAULT).addOnSuccessListener(doc -> {
            if (doc.exists()) {
                tripName = doc.getString("tripName");
                ((TextView) findViewById(R.id.txt_details_trip_name)).setText(getString(R.string.format_trip_name_header, tripName));
                ((TextView) findViewById(R.id.txt_details_destination)).setText(doc.getString("destination"));
                ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(doc.getString("startDate")));

                startDateFromDatabase = doc.getString("startDate");
                endDateFromDatabase = doc.getString("endDate");
                membersRaw = doc.getString("members");
                String inactiveRaw = doc.getString("inactiveMembers");

                updateMemberGrids(membersRaw, inactiveRaw);
                refreshSummaryCards();
            }
        });
    }

    private void updateMemberGrids(String activeRaw, String inactiveRaw) {
        // 1. Process Active Members
        ArrayList<String> activeMembers = new ArrayList<>();
        if (activeRaw != null && !activeRaw.isEmpty()) {
            activeMembers.addAll(Arrays.asList(activeRaw.split(",")));
        }

        ((TextView) findViewById(R.id.txt_details_member_count)).setText(String.valueOf(activeMembers.size()));

        GridLayout gridActive = findViewById(R.id.grid_members);
        gridActive.removeAllViews();
        for (String activeName : activeMembers) {
            addMemberButton(activeName.trim(), gridActive);
        }

        // 2. Process Inactive Members directly from Firestore!
        GridLayout gridInactive = findViewById(R.id.grid_inactive_members);
        TextView txtInactiveHeader = findViewById(R.id.txt_inactive_members_header);

        ArrayList<String> inactiveMembers = new ArrayList<>();
        if (inactiveRaw != null && !inactiveRaw.isEmpty()) {
            inactiveMembers.addAll(Arrays.asList(inactiveRaw.split(",")));
        }

        if (!inactiveMembers.isEmpty()) {
            txtInactiveHeader.setVisibility(View.VISIBLE);
            gridInactive.setVisibility(View.VISIBLE);
            gridInactive.removeAllViews();
            for (String inactiveName : inactiveMembers) {
                // Now using addMemberButton for Inactive as well
                addMemberButton(inactiveName.trim(), gridInactive);
            }
        } else {
            txtInactiveHeader.setVisibility(View.GONE);
            gridInactive.setVisibility(View.GONE);
        }
    }

    // 1. Update your refreshSummaryCards to use the helper method
    private void refreshSummaryCards() {
        // We use "new" to implement the interface properly
        TripFinanceCalculator.calculateFinances(tripId, new TripFinanceCalculator.FinanceResultListener() {
            @Override
            public void onStart() {
                // Logic for onStart (e.g., show loading)
                TextView txtExpenses = findViewById(R.id.txt_details_total_expenses);
                if (txtExpenses != null) txtExpenses.setText("Loading...");
            }

            @Override
            public void onResult(double totalExp, double totalRec, double fundBal) {
                // Logic for onResult (update UI)
                TextView txtExpenses = findViewById(R.id.txt_details_total_expenses);
                TextView txtReceipts = findViewById(R.id.txt_details_total_receipts);
                TextView txtFundBalance = findViewById(R.id.txt_details_fund_balance);

                if (txtExpenses != null) txtExpenses.setText(formatCurrency(totalExp));
                if (txtReceipts != null) txtReceipts.setText(formatCurrency(totalRec));
                if (txtFundBalance != null) txtFundBalance.setText(formatCurrency(fundBal));
            }
        });
    }

    private String formatCurrency(double amount) {
        // Cleaned up the 'format:' artifact that was causing the syntax error
        return String.format(java.util.Locale.US, "₹%.2f", amount);
    }

    private void addMemberButton(String mName, GridLayout grid) {
        Button btn = new Button(this);
        btn.setText(mName);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(this, MemberLedgerActivity.class);
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

    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete this trip from Cloud?")
                .setPositiveButton("Yes, Delete", (dialog, which) ->
                        db.collection("Trips").document(tripId).delete()
                                .addOnSuccessListener(a -> {
                                    Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                )
                .setNegativeButton("Cancel", null)
                .show();
    }
}