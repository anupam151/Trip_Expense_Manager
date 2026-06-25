package com.example.tripexpensemanager;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class ViewTripActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Linking your 4-button category selection layout file
        setContentView(R.layout.activity_view_trip);

        // Binding your 4 operational category buttons using their layout XML resource IDs
        // Note: Change these specific resource IDs if your XML uses different names (e.g., btn_ongoing)
        Button btnOngoing = findViewById(R.id.btn_ongoing_trips);
        Button btnUpcoming = findViewById(R.id.btn_upcoming_trips);
        Button btnEnded = findViewById(R.id.btn_ended_trips);
        Button btnAllTrips = findViewById(R.id.btn_all_trips);

        // 1. Ongoing Category Click Setup Rule
        btnOngoing.setOnClickListener(v -> {
            Intent intent = new Intent(ViewTripActivity.this, TripListActivity.class);
            intent.putExtra("CATEGORY_TITLE", "Ongoing Trips");
            startActivity(intent);
        });

        // 2. Upcoming Category Click Setup Rule
        btnUpcoming.setOnClickListener(v -> {
            Intent intent = new Intent(ViewTripActivity.this, TripListActivity.class);
            intent.putExtra("CATEGORY_TITLE", "Upcoming Trips");
            startActivity(intent);
        });

        // 3. Ended Category Click Setup Rule
        btnEnded.setOnClickListener(v -> {
            Intent intent = new Intent(ViewTripActivity.this, TripListActivity.class);
            intent.putExtra("CATEGORY_TITLE", "Ended Trips");
            startActivity(intent);
        });

        // 4. All Trips Master Category Click Setup Rule
        btnAllTrips.setOnClickListener(v -> {
            Intent intent = new Intent(ViewTripActivity.this, TripListActivity.class);
            intent.putExtra("CATEGORY_TITLE", "All Trips");
            startActivity(intent);
        });
    }
}