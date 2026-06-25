package com.example.tripexpensemanager;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TripListActivity extends AppCompatActivity implements TripAdapter.OnTripActionListener {

    private RecyclerView recyclerView;
    private TextView txtEmptyMessage;
    private TripDatabaseHelper dbHelper;
    private TripAdapter adapter;
    private final ArrayList<TripModel> tripList = new ArrayList<>();
    private String currentCategoryLabel;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);

        recyclerView = findViewById(R.id.recycler_view_trips);
        txtEmptyMessage = findViewById(R.id.txt_empty_trips_message);
        TextView txtCategoryTitle = findViewById(R.id.txt_trip_list_category_title);
        MaterialButton btnHeaderAddNewTrip = findViewById(R.id.btn_header_add_new_trip);

        dbHelper = new TripDatabaseHelper(this);

        if (getIntent() != null && getIntent().getStringExtra("CATEGORY_TITLE") != null) {
            currentCategoryLabel = getIntent().getStringExtra("CATEGORY_TITLE");
        } else {
            currentCategoryLabel = "All Trips";
        }

        txtCategoryTitle.setText(currentCategoryLabel);
        txtEmptyMessage.setText(getString(R.string.msg_no_trip_added));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(tripList, this);
        recyclerView.setAdapter(adapter);

        btnHeaderAddNewTrip.setOnClickListener(v -> startActivity(new Intent(TripListActivity.this, CreateTripActivity.class)));

        loadAndFilterTrips();
    }

    private void loadAndFilterTrips() {
        // FIXED: Eliminated notifyDataSetChanged() entirely in favor of high-efficiency specific range notifications
        int previousSize = tripList.size();
        tripList.clear();

        if (previousSize > 0) {
            adapter.notifyItemRangeRemoved(0, previousSize);
        }

        Cursor cursor = dbHelper.getAllTripsCursor();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        Date todayDate = cal.getTime();

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String tripId = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_TRIP_NAME));
                String destination = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_DESTINATION));
                String members = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBERS));
                int count = cursor.getInt(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_MEMBER_COUNT));
                String startStr = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_START_DATE));
                String endStr = cursor.getString(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_END_DATE));
                int pinnedVal = cursor.getInt(cursor.getColumnIndexOrThrow(TripDatabaseHelper.COLUMN_IS_PINNED));

                boolean shouldAddTrip = false;
                if (currentCategoryLabel.equalsIgnoreCase("All Trips")) {
                    shouldAddTrip = true;
                } else {
                    try {
                        Date startDate = dateFormatter.parse(startStr.trim());
                        Date endDate = dateFormatter.parse(endStr.trim());
                        if (currentCategoryLabel.equalsIgnoreCase("Upcoming Trips") && startDate != null && startDate.after(todayDate)) {
                            shouldAddTrip = true;
                        } else if (currentCategoryLabel.equalsIgnoreCase("Ongoing Trips") && startDate != null && endDate != null && !startDate.after(todayDate) && !endDate.before(todayDate)) {
                            shouldAddTrip = true;
                        } else if (currentCategoryLabel.equalsIgnoreCase("Ended Trips") && endDate != null && endDate.before(todayDate)) {
                            shouldAddTrip = true;
                        }
                    } catch (ParseException ignored) {
                        shouldAddTrip = true;
                    }
                }

                if (shouldAddTrip) {
                    TripModel tripObj = new TripModel(tripId, name, destination, members, count, startStr, endStr);
                    tripObj.setIsPinnedState(pinnedVal);
                    tripList.add(tripObj);
                }
            }
            cursor.close();
        }

        txtEmptyMessage.setVisibility(tripList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(tripList.isEmpty() ? View.GONE : View.VISIBLE);

        // FIXED: Using highly specific item range insertion notifications
        if (!tripList.isEmpty()) {
            adapter.notifyItemRangeInserted(0, tripList.size());
        }
    }

    @Override
    public void onPinToggleClick(TripModel trip, int position) {
        int result = dbHelper.toggleTripPinStatus(trip.getTripId());

        if (result == 1) {
            trip.setIsPinnedState(1);
            Toast.makeText(this, "'" + trip.getTripName() + "' pinned successfully!", Toast.LENGTH_SHORT).show();
        } else if (result == 0) {
            trip.setIsPinnedState(0);
            Toast.makeText(this, "'" + trip.getTripName() + "' unpinned successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Limit reached! You can pin a maximum of 2 trips.", Toast.LENGTH_LONG).show();
            return;
        }

        if (position >= 0 && position < tripList.size()) {
            adapter.notifyItemChanged(position);
        }
    }

    @Override
    public void onTripItemClick(TripModel trip) {
        Intent intent = new Intent(this, TripLedgerActivity.class);
        intent.putExtra("TRIP_ID", trip.getTripId());
        startActivity(intent);
    }

    @Override
    public void onEditClick(TripModel trip) {
        Intent intent = new Intent(this, UpdateTripActivity.class);
        intent.putExtra("TRIP_ID", trip.getTripId());
        intent.putExtra("TRIP_NAME", trip.getTripName());
        intent.putExtra("TRIP_DESTINATION", trip.getDestination());
        intent.putExtra("TRIP_MEMBERS", trip.getMembersListString());
        intent.putExtra("TRIP_START_DATE", trip.getStartDate());
        intent.putExtra("TRIP_END_DATE", trip.getEndDate());
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(TripModel trip) {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete '" + trip.getTripName() + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    dbHelper.deleteTrip(trip.getTripId());
                    loadAndFilterTrips();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF000000);
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF000000);
    }

    @Override
    public void onAddExpenseClick(TripModel trip) {
        Intent intent = new Intent(this, AddExpenseActivity.class);
        intent.putExtra("TRIP_ID", trip.getTripId());
        intent.putExtra("TRIP_MEMBERS", trip.getMembersListString());
        startActivity(intent);
    }

    @Override
    public void onAddPaymentClick(TripModel trip) {
        Intent intent = new Intent(this, AddPaymentActivity.class);
        intent.putExtra("TRIP_ID", trip.getTripId());
        intent.putExtra("TRIP_MEMBERS", trip.getMembersListString());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAndFilterTrips();
    }
}