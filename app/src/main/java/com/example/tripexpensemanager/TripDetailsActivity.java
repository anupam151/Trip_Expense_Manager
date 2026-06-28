package com.example.tripexpensemanager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
            ((TextView) findViewById(R.id.txt_details_fund_balance)).setText(String.format(Locale.US, "₹%.2f", db.getFundBalance(tripId)));
            ((TextView) findViewById(R.id.txt_details_total_expenses)).setText(String.format(Locale.US, "₹%.2f", db.getTripTotalExpenses(tripId)));
            ((TextView) findViewById(R.id.txt_details_total_receipts)).setText(String.format(Locale.US, "₹%.2f", db.getTripTotalPaymentsReceived(tripId)));

            if (membersRaw != null && !membersRaw.isEmpty()) {
                String[] memberList = membersRaw.split(",");
                ((TextView) findViewById(R.id.txt_details_member_count)).setText(String.valueOf(memberList.length));
                GridLayout memberGrid = findViewById(R.id.grid_members);

                for (String mName : memberList) {
                    Button btn = new Button(this);
                    btn.setText(mName.trim());
                    btn.setAllCaps(false);
                    btn.setEnabled(true); // ENABLED: Ready for Individual Ledger

                    // Add click listener for Individual Member Ledger
                    btn.setOnClickListener(v -> {
                        Intent intent = new Intent(TripDetailsActivity.this, MemberLedgerActivity.class);
                        intent.putExtra("TRIP_ID", tripId);
                        intent.putExtra("MEMBER_NAME", mName.trim());
                        startActivity(intent);
                    });

                    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                    params.width = 0; params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    params.setMargins(8, 8, 8, 8);
                    btn.setLayoutParams(params);
                    memberGrid.addView(btn);
                }
            } else {
                ((TextView) findViewById(R.id.txt_details_member_count)).setText("0");
            }
        }
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