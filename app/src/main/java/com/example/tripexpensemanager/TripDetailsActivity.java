package com.example.tripexpensemanager;

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

        // REFORMAT DATE LOGIC
        ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(date));

        try (TripDatabaseHelper db = new TripDatabaseHelper(this)) {
            // ... (rest of your metrics and grid logic remains the same)
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
                    btn.setEnabled(false);
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

    // Helper method to reformat the date
    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "N/A";

        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yy", Locale.US);

        try {
            Date date = inputFormat.parse(dateStr);
            // Add a null check here to satisfy the compiler
            if (date != null) {
                return outputFormat.format(date);
            }
        } catch (ParseException e) {
            Log.e("TripDetailsActivity", "Error parsing date: " + dateStr, e);
        }
        return dateStr; // Fallback to original string if parsing failed or date was null
    }
}