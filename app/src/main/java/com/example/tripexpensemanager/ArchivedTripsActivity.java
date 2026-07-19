package com.example.tripexpensemanager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ArchivedTripsActivity extends BaseDrawerActivity {

    private final List<DocumentSnapshot> masterArchivedList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredList = new ArrayList<>();

    private ArchivedTripAdapter adapter;
    private FirebaseFirestore db;
    private String userEmail;

    // --- Search & Sort Variables matching TripListActivity ---
    private int currentSortOption = 1; // Default to Departure Ascending
    private String currentSearchQuery = "";
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private TextView txtEmptyMessage;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView txtCurrentSort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_trips);

        // 1. Setup Universal Drawer
        setupUniversalDrawer(R.id.drawer_layout, R.id.navigation_view);

        // 2. Handle the Back button to close the drawer gracefully
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

        // 3. Hamburger Menu
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // 4. Firebase Setup
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userEmail = user.getEmail();
        }

        // 5. UI Mappings
        TextView txtCategoryTitle = findViewById(R.id.txt_trip_list_category_title);
        txtCategoryTitle.setText("Archived Trips");

        txtEmptyMessage = findViewById(R.id.txt_empty_trips_message);
        txtCurrentSort = findViewById(R.id.txt_current_sort);
        txtCurrentSort.setText("Departure ▲");

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#85022E"));
        swipeRefreshLayout.setOnRefreshListener(this::loadArchivedTrips);

        RecyclerView recyclerView = findViewById(R.id.recycler_view_trips);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Note: Adapter uses DocumentSnapshot so we don't break your existing ArchivedTripAdapter
        adapter = new ArchivedTripAdapter(filteredList, doc -> unarchiveTrip(doc.getId()));
        recyclerView.setAdapter(adapter);

        // 6. Bottom Navigation
        setupBottomNavigation();

        // 7. Advanced Dual-Popup Sorting Setup
        LinearLayout btnSortDropdown = findViewById(R.id.btn_sort_dropdown);
        if (btnSortDropdown != null) {
            btnSortDropdown.setOnClickListener(v -> showSortPopupMenu(v, txtCurrentSort));
        }

        // 8. Advanced Search Setup (Typing + Keyboard Hiding)
        EditText etSearch = findViewById(R.id.edit_search_trips);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s.toString().toLowerCase().trim();
                    applyFilterAndSort();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            // Hide keyboard on "Done"
            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    etSearch.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    return true;
                }
                return false;
            });
        }

        // 9. Initial Load
        swipeRefreshLayout.setRefreshing(true);
        loadArchivedTrips();
    }

    // ==========================================
    // --- BOTTOM NAVIGATION ---
    // ==========================================
    private void setupBottomNavigation() {
        View btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });

        View btnUtility = findViewById(R.id.btnUtility);
        if (btnUtility != null) btnUtility.setOnClickListener(v ->startActivity(new Intent(this, UtilityActivity.class)));

        View btnViewTrips = findViewById(R.id.view_trips);
        if (btnViewTrips != null) btnViewTrips.setOnClickListener(v ->startActivity(new Intent(this, TripListActivity.class)));

        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) btnSettings.setOnClickListener(v ->startActivity(new Intent(this, SettingsActivity.class)));
    }

    // ==========================================
    // --- ADVANCED SORTING ENGINE ---
    // ==========================================
    private void showSortPopupMenu(View anchor, TextView txtCurrentSort) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END);
        popup.getMenu().add(0, 10, 0, "Departure");
        popup.getMenu().add(0, 20, 0, "Destination"); // Shifted Destination to ID 20

        popup.setOnMenuItemClickListener(item -> {
            String safeTitle = item.getTitle() != null ? item.getTitle().toString() : "";
            showOrderPopupMenu(anchor, txtCurrentSort, safeTitle, item.getItemId());
            return true;
        });
        popup.show();
    }

    private void showOrderPopupMenu(View anchor, TextView txtCurrentSort, String categoryName, int categoryId) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END);

        int ascId = 0, descId = 0;
        if (categoryId == 10) { ascId = 1; descId = 2; }       // Departure
        else if (categoryId == 20) { ascId = 3; descId = 4; }  // Destination

        popup.getMenu().add(0, ascId, 0, "Ascending");
        popup.getMenu().add(0, descId, 0, "Descending");

        popup.setOnMenuItemClickListener(item -> {
            String safeTitle = item.getTitle() != null ? item.getTitle().toString() : "";
            String arrow = safeTitle.equals("Ascending") ? "▲" : "▼";
            txtCurrentSort.setText(categoryName + " " + arrow);

            currentSortOption = item.getItemId();
            applyFilterAndSort();
            return true;
        });
        popup.show();
    }

    private int compareDates(String d1, String d2, boolean ascending) {
        try {
            Date date1 = (d1 != null && !d1.isEmpty()) ? dateFormatter.parse(d1) : new Date(0);
            Date date2 = (d2 != null && !d2.isEmpty()) ? dateFormatter.parse(d2) : new Date(0);
            if (date1 == null) date1 = new Date(0);
            if (date2 == null) date2 = new Date(0);
            return ascending ? date1.compareTo(date2) : date2.compareTo(date1);
        } catch (ParseException e) {
            return 0;
        }
    }

    private int compareStrings(String s1, String s2, boolean ascending) {
        String str1 = s1 != null ? s1.toLowerCase() : "";
        String str2 = s2 != null ? s2.toLowerCase() : "";
        return ascending ? str1.compareTo(str2) : str2.compareTo(str1);
    }

    // ==========================================
    // --- DATA FETCHING & FILTERING ---
    // ==========================================
    private void loadArchivedTrips() {
        if (userEmail == null) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        db.collection("Users").document(userEmail).get()
                .addOnSuccessListener(userDoc -> {

                    List<String> cloudArchivedIds = new java.util.ArrayList<>();
                    if (userDoc.exists() && userDoc.contains("archivedTripIds")) {
                        Object rawList = userDoc.get("archivedTripIds");
                        if (rawList instanceof java.util.List<?>) {
                            for (Object item : (java.util.List<?>) rawList) {
                                if (item instanceof String) cloudArchivedIds.add((String) item);
                            }
                        }
                    }

                    if (cloudArchivedIds.isEmpty()) {
                        masterArchivedList.clear();
                        applyFilterAndSort();
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        return;
                    }

                    final List<String> finalArchivedIds = cloudArchivedIds;

                    db.collection("Trips").whereArrayContains("sharedEmails", userEmail).get()
                            .addOnSuccessListener(queryDocumentSnapshots -> {
                                masterArchivedList.clear();

                                for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                                    if (finalArchivedIds.contains(doc.getId())) {
                                        masterArchivedList.add(doc);
                                    }
                                }
                                applyFilterAndSort();
                                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                            });
                })
                .addOnFailureListener(e -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void applyFilterAndSort() {
        filteredList.clear();

        // 1. FILTERING (Deep Search)
        if (currentSearchQuery.isEmpty()) {
            filteredList.addAll(masterArchivedList);
        } else {
            for (DocumentSnapshot doc : masterArchivedList) {

                // Grab the raw strings once
                String rawName = doc.getString("tripName");
                String rawDest = doc.getString("destination");
                String rawMembers = doc.getString("members");
                String rawInactive = doc.getString("inactiveMembers");

                // Now safely convert them to lowercase
                String name = rawName != null ? rawName.toLowerCase() : "";
                String dest = rawDest != null ? rawDest.toLowerCase() : "";
                String members = rawMembers != null ? rawMembers.toLowerCase() : "";
                String inactive = rawInactive != null ? rawInactive.toLowerCase() : "";

                if (name.contains(currentSearchQuery) || dest.contains(currentSearchQuery) ||
                        members.contains(currentSearchQuery) || inactive.contains(currentSearchQuery)) {
                    filteredList.add(doc);
                }
            }
        }

        // 2. SORTING (Using the dual popup selection)
        // 2. SORTING (Using the dual popup selection)
        filteredList.sort((d1, d2) -> {
            try {
                switch (currentSortOption) {
                    case 1: return compareDates(d1.getString("startDate"), d2.getString("startDate"), true); // Departure ▲
                    case 2: return compareDates(d1.getString("startDate"), d2.getString("startDate"), false); // Departure ▼
                    case 3: return compareStrings(d1.getString("destination"), d2.getString("destination"), true); // Destination ▲
                    case 4: return compareStrings(d1.getString("destination"), d2.getString("destination"), false); // Destination ▼
                    default: return 0;
                }
            } catch (Exception e) {
                return 0;
            }
        });

        // 3. UI Update
        adapter.notifyDataSetChanged();
        if (filteredList.isEmpty()) {
            txtEmptyMessage.setVisibility(View.VISIBLE);
            txtEmptyMessage.setText("No archived trips found.");
        } else {
            txtEmptyMessage.setVisibility(View.GONE);
        }
    }

    private void unarchiveTrip(String tripId) {
        if (userEmail == null) return;

        db.collection("Users").document(userEmail)
                .update("archivedTripIds", FieldValue.arrayRemove(tripId))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Trip restored to dashboard.", Toast.LENGTH_SHORT).show();
                    loadArchivedTrips();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to restore trip.", Toast.LENGTH_SHORT).show());
    }
}