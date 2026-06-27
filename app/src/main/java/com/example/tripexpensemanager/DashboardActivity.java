package com.example.tripexpensemanager;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class DashboardActivity extends AppCompatActivity {

    private TextView lblRecentHeading;
    private LinearLayout containerPinnedTripsStack;
    private TripDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        dbHelper = new TripDatabaseHelper(this);

        MaterialButton btnCreateTrip = findViewById(R.id.btn_dash_create_trip);
        MaterialButton btnViewTrips = findViewById(R.id.btn_dash_view_trips);
        lblRecentHeading = findViewById(R.id.lbl_recent_trip_heading);
        containerPinnedTripsStack = findViewById(R.id.container_pinned_trips_stack);

        TextView txtDeveloperBranding = findViewById(R.id.txt_dash_developer_branding);
        String styledSignatureText = getString(R.string.dev_branding_signature_placeholder, "<b><font color='#1E88E5'>Anupam</font></b>");
        txtDeveloperBranding.setText(Html.fromHtml(styledSignatureText, Html.FROM_HTML_MODE_LEGACY));

        btnCreateTrip.setOnClickListener(v -> startActivity(new Intent(this, CreateTripActivity.class)));
        btnViewTrips.setOnClickListener(v -> startActivity(new Intent(this, TripListActivity.class)));
    }

    private void updatePinnedWorkspace() {
        containerPinnedTripsStack.removeAllViews();
        Cursor cursor = dbHelper.getPinnedTripsCursor();

        if (cursor == null || cursor.getCount() == 0) {
            lblRecentHeading.setVisibility(View.GONE);
            if (cursor != null) cursor.close();
            return;
        }

        lblRecentHeading.setVisibility(View.VISIBLE);
        int itemIndex = 1;

        float scale = getResources().getDisplayMetrics().density;
        int marginHorizontalPx = Math.round(2 * scale);
        int marginBottomPx = Math.round(8 * scale);

        while (cursor.moveToNext()) {
            String tripId = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_ID));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_NAME));
            String destination = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_DESTINATION));
            String members = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBERS));
            int count = cursor.getInt(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBER_COUNT));
            String startDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_START_DATE));
            String endDate = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_END_DATE));

            TripModel trip = new TripModel(tripId, name, destination, members, count, startDate, endDate);
            trip.setIsPinnedState(1);

            View cardView = LayoutInflater.from(this).inflate(R.layout.item_trip, containerPinnedTripsStack, false);

            TextView txtTripName = cardView.findViewById(R.id.txt_item_trip_name);
            TextView txtDestination = cardView.findViewById(R.id.txt_item_destination);
            TextView txtMemberCount = cardView.findViewById(R.id.txt_item_member_count);

            // FIXED: Link to the new XML ID
            TextView txtFundBalance = cardView.findViewById(R.id.txt_item_fund_balance);

            TextView txtStartDate = cardView.findViewById(R.id.txt_item_start_date);
            TextView btnPin = cardView.findViewById(R.id.btn_item_pin);
            TextView btnEdit = cardView.findViewById(R.id.btn_item_edit);
            TextView btnDelete = cardView.findViewById(R.id.btn_item_delete);
            MaterialButton btnAddExpense = cardView.findViewById(R.id.btn_item_add_expense);
            MaterialButton btnAddPayment = cardView.findViewById(R.id.btn_item_add_payment);

            double totalExpense = dbHelper.getTripTotalExpenses(tripId);
            double totalReceived = dbHelper.getTripTotalPaymentsReceived(tripId);

            // FIXED: Fetch real-time fund balance
            double fundBalance = dbHelper.getFundBalance(tripId);

            TextView txtTotalExpense = cardView.findViewById(R.id.txt_item_total_expense);
            TextView txtTotalReceived = cardView.findViewById(R.id.txt_item_total_received);

            if (txtTotalExpense != null) txtTotalExpense.setText(getString(R.string.fmt_dash_currency_rupees, totalExpense));
            if (txtTotalReceived != null) txtTotalReceived.setText(getString(R.string.fmt_dash_currency_rupees, totalReceived));

            // FIXED: Set the formatted fund balance string
            if (txtFundBalance != null) txtFundBalance.setText(String.format(java.util.Locale.US, "Fund Balance: ₹%.2f", fundBalance));

            txtTripName.setText(getString(R.string.fmt_dash_pinned_title, itemIndex, name));
            txtDestination.setText(getString(R.string.fmt_dash_destination, destination));
            txtMemberCount.setText(getString(R.string.fmt_dash_member_count, count));
            txtStartDate.setText(getString(R.string.fmt_dash_start_date, startDate));

            btnPin.setText(getString(R.string.action_unpin));
            btnPin.setTextColor(0xFF2E7D32);

            // Inside updatePinnedWorkspace()
            cardView.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, TripDetailsActivity.class);
                intent.putExtra("TRIP_ID", trip.getTripId());
                intent.putExtra("TRIP_NAME", trip.getTripName());
                intent.putExtra("DESTINATION", trip.getDestination());
                intent.putExtra("START_DATE", trip.getStartDate());
                intent.putExtra("MEMBERS", trip.getMembersListString()); // Ensure getMembersListString() returns comma-separated names
                startActivity(intent);
            });

            btnPin.setOnClickListener(v -> {
                dbHelper.toggleTripPinStatus(trip.getTripId());
                Toast.makeText(DashboardActivity.this, "'" + name + "' unpinned successfully!", Toast.LENGTH_SHORT).show();
                updatePinnedWorkspace();
            });

            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, UpdateTripActivity.class);
                intent.putExtra("TRIP_ID", trip.getTripId());
                intent.putExtra("TRIP_NAME", trip.getTripName());
                intent.putExtra("TRIP_DESTINATION", trip.getDestination());
                intent.putExtra("TRIP_MEMBERS", trip.getMembersListString());
                intent.putExtra("TRIP_START_DATE", trip.getStartDate());
                intent.putExtra("TRIP_END_DATE", trip.getEndDate());
                startActivity(intent);
            });

            btnDelete.setOnClickListener(v -> {
                AlertDialog alertDialog = new AlertDialog.Builder(DashboardActivity.this)
                        .setTitle("Delete Trip")
                        .setMessage("Are you sure you want to delete '" + name + "'?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("Yes, Delete", (dialog, which) -> {
                            dbHelper.deleteTrip(tripId);
                            updatePinnedWorkspace();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create();
                alertDialog.show();
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF000000);
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF000000);
            });

            btnAddExpense.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AddExpenseActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                intent.putExtra("TRIP_MEMBERS", members);
                startActivity(intent);
            });

            btnAddPayment.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AddPaymentActivity.class);
                intent.putExtra("TRIP_ID", tripId);
                intent.putExtra("TRIP_MEMBERS", members);
                startActivity(intent);
            });

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            layoutParams.setMargins(marginHorizontalPx, marginBottomPx, marginHorizontalPx, marginBottomPx);
            cardView.setLayoutParams(layoutParams);

            containerPinnedTripsStack.addView(cardView);
            itemIndex++;
        }
        cursor.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePinnedWorkspace();
    }
}