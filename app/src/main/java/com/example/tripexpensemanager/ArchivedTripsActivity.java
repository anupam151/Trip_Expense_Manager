package com.example.tripexpensemanager;

//import android.content.Intent;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
//import java.util.Set;

// 1. Extend BaseDrawerActivity so the side menu works!
public class ArchivedTripsActivity extends BaseDrawerActivity {

    private final List<DocumentSnapshot> archivedTripsList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();

    private ArchivedTripAdapter adapter;
    private FirebaseFirestore db;
    private String userEmail;
    private boolean isSortAscending = true;

    private TextView txtEmptyMessage;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView txtCurrentSort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_trips);


        View btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(ArchivedTripsActivity.this, DashboardActivity.class);
                startActivity(intent);
                finish();
            });
        }

        View btnUtility = findViewById(R.id.btnUtility);
        if (btnUtility != null) {
            btnUtility.setOnClickListener(v -> {
                Intent intent = new Intent(ArchivedTripsActivity.this, UtilityActivity.class);
                startActivity(intent);
            });
        }

        View btnViewTrips = findViewById(R.id.view_trips);
        if (btnViewTrips != null) {
            btnViewTrips.setOnClickListener(v -> {
                Intent intent = new Intent(ArchivedTripsActivity.this, TripListActivity.class);
                startActivity(intent);
            });
        }

        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                Intent intent = new Intent(ArchivedTripsActivity.this, SettingsActivity.class);
                startActivity(intent);
            });
        }


        // 2. Setup the Universal Drawer using IDs from your layout
        setupUniversalDrawer(R.id.drawer_layout, R.id.navigation_view);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userEmail = user.getEmail();
        }

        // 3. Change the title to indicate we are in the Archive
        TextView txtCategoryTitle = findViewById(R.id.txt_trip_list_category_title);
        txtCategoryTitle.setText("Archived Trips");

        // 4. Map the UI elements
        txtEmptyMessage = findViewById(R.id.txt_empty_trips_message);
        txtCurrentSort = findViewById(R.id.txt_current_sort);
        txtCurrentSort.setText("Dest A-Z ▲");

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#85022E"));
        swipeRefreshLayout.setOnRefreshListener(this::loadArchivedTrips);

        RecyclerView recyclerView = findViewById(R.id.recycler_view_trips);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ArchivedTripAdapter(filteredList, doc -> unarchiveTrip(doc.getId()));
        recyclerView.setAdapter(adapter);

        // 5. Drawer Menu Button
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        btnOpenDrawer.setOnClickListener(v -> {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // 7. Sort Dropdown
        LinearLayout btnSort = findViewById(R.id.btn_sort_dropdown);
        btnSort.setOnClickListener(v -> {
            isSortAscending = !isSortAscending;
            txtCurrentSort.setText(isSortAscending ? "Dest A-Z ▲" : "Dest Z-A ▼");
            loadArchivedTrips();
        });

        // 8. Search Bar
        EditText etSearch = findViewById(R.id.edit_search_trips);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTrips(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Load the data!
        swipeRefreshLayout.setRefreshing(true);
        loadArchivedTrips();
    }
    // How to fetch the archived trips from the cloud
    private void loadArchivedTrips() {
        if (userEmail == null) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        // 1. Fetch the user's private list of archived IDs first
        db.collection("Users").document(userEmail).get()
                .addOnSuccessListener(userDoc -> {

                    // Get cloud array safely
                    List<String> cloudArchivedIds = new java.util.ArrayList<>();
                    if (userDoc.exists() && userDoc.contains("archivedTripIds")) {
                        Object rawList = userDoc.get("archivedTripIds");

                        // Safely check that it is a list, and only pull out the Strings
                        if (rawList instanceof java.util.List<?>) {
                            for (Object item : (java.util.List<?>) rawList) {
                                if (item instanceof String) {
                                    cloudArchivedIds.add((String) item);
                                }
                            }
                        }
                    }

                    // Just check if it's empty!
                    if (cloudArchivedIds.isEmpty()) {
                        archivedTripsList.clear();
                        filterTrips("");
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        return;
                    }
                    final List<String> finalArchivedIds = cloudArchivedIds;

                    // 2. Fetch the trips and filter against the cloud list
                    db.collection("Trips")
                            .whereArrayContains("sharedEmails", userEmail)
                            .get()
                            .addOnSuccessListener(queryDocumentSnapshots -> {
                                archivedTripsList.clear();

                                for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                                    if (finalArchivedIds.contains(doc.getId())) {
                                        archivedTripsList.add(doc);
                                    }
                                }

                                // Sort and update UI
                                archivedTripsList.sort((d1, d2) -> {
                                    String dest1 = d1.getString("destination");
                                    String dest2 = d2.getString("destination");
                                    String name1 = (dest1 != null) ? dest1 : "";
                                    String name2 = (dest2 != null) ? dest2 : "";
                                    return isSortAscending ? name1.compareToIgnoreCase(name2) : name2.compareToIgnoreCase(name1);
                                });

                                EditText etSearch = findViewById(R.id.edit_search_trips);
                                filterTrips(etSearch != null ? etSearch.getText().toString() : "");

                                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                            });
                })
                .addOnFailureListener(e -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

                    // 1. Show the exact error on the screen
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();

                    // 2. Print the full red error trace to Logcat
                    android.util.Log.e("FirestoreError", "Why did it fail?", e);
                });
    }

    // Your updated Restore/Unarchive action
    private void unarchiveTrip(String tripId) {
        if (userEmail == null) return;

        // Remove the ID from the array
        db.collection("Users").document(userEmail)
                .update("archivedTripIds", FieldValue.arrayRemove(tripId))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Trip restored to dashboard.", Toast.LENGTH_SHORT).show();
                    loadArchivedTrips(); // Reload the list
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to restore trip.", Toast.LENGTH_SHORT).show());
    }
    private void filterTrips(String text) {
        filteredList.clear();
        if (text.isEmpty()) {
            filteredList.addAll(archivedTripsList);
        } else {
            String lowerText = text.toLowerCase();
            for (DocumentSnapshot doc : archivedTripsList) {
                String dest = doc.getString("destination");
                String name = doc.getString("tripName");

                if ((dest != null && dest.toLowerCase().contains(lowerText)) ||
                        (name != null && name.toLowerCase().contains(lowerText))) {
                    filteredList.add(doc);
                }
            }
        }

        // Handle Empty State Visibility
        if (filteredList.isEmpty()) {
            txtEmptyMessage.setVisibility(View.VISIBLE);
            txtEmptyMessage.setText("No archived trips found.");
        } else {
            txtEmptyMessage.setVisibility(View.GONE);
        }

        adapter.notifyDataSetChanged();
    }
}