package com.example.tripexpensemanager;

import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
//import java.util.Collections;
import java.util.Date;
import java.util.Locale;

//import android.view.Gravity;
//import androidx.appcompat.widget.PopupMenu;

@SuppressWarnings("deprecation")
public class TripListActivity extends AppCompatActivity implements TripAdapter.OnTripActionListener {

    private TextView txtEmptyMessage;
    private TripAdapter adapter;
    private final ArrayList<TripModel> tripList = new ArrayList<>();
    private String currentCategoryLabel;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    // --- NEW: Keep track of current sort option ---
    private int currentSortOption = 1; // Default to Departure Ascending

    private FirebaseFirestore db;
    private TripDatabaseHelper dbHelper;
    private LedgerExportManager exportManager;
    private String selectedTripIdForExport = null;

    private final ActivityResultLauncher<String> createMasterPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null && exportManager != null && selectedTripIdForExport != null) {
                    exportManager.exportAllMembersToSinglePdf(uri, selectedTripIdForExport);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);

        // Warning fix: Moved recyclerView to a local variable
        RecyclerView recyclerView = findViewById(R.id.recycler_view_trips);
        txtEmptyMessage = findViewById(R.id.txt_empty_trips_message);
        TextView txtCategoryTitle = findViewById(R.id.txt_trip_list_category_title);

        LinearLayout btnHeaderAddNewTrip = findViewById(R.id.btn_header_add_new_trip);

        // --- Bind the Sort Dropdown ---
        LinearLayout btnSortDropdown = findViewById(R.id.btn_sort_dropdown);
        TextView txtCurrentSort = findViewById(R.id.txt_current_sort);

        if (btnSortDropdown != null && txtCurrentSort != null) {
            // 1. Force the default text to match the default sorting math!
            txtCurrentSort.setText("Departure Date ▲");

            btnSortDropdown.setOnClickListener(v -> showSortPopupMenu(v, txtCurrentSort));
        }

        db = FirebaseFirestore.getInstance();
        dbHelper = new TripDatabaseHelper(this); // Preserved for Legacy Export compatibility
        exportManager = new LedgerExportManager(this, dbHelper);

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

        if (btnHeaderAddNewTrip != null) {
            btnHeaderAddNewTrip.setOnClickListener(v -> startActivity(new Intent(TripListActivity.this, CreateTripActivity.class)));
        }

        LinearLayout btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(TripListActivity.this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        LinearLayout btnExportAll = findViewById(R.id.unv_btn_all_individual_to_one_pdf);
        if (btnExportAll != null) {
            btnExportAll.setOnClickListener(v -> fetchTripsAndShowSelectionDialog(true));
        }

        LinearLayout btnCompleteLedger = findViewById(R.id.unv_btn_complete_ledger);
        if (btnCompleteLedger != null) {
            btnCompleteLedger.setOnClickListener(v -> fetchTripsAndShowSelectionDialog(false));
        }

        loadAndFilterTrips();
    }

    // ==========================================
    // --- NEW: THE COMPLETE SORTING ENGINE ---
    // ==========================================

    private void showSortPopupMenu(View anchor, TextView txtCurrentSort) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END);

        popup.getMenu().add(0, 10, 0, "Departure Date");
        popup.getMenu().add(0, 20, 0, "Arrival Date");
        popup.getMenu().add(0, 30, 0, "Destination");
        popup.getMenu().add(0, 40, 0, "Total Expense");

        popup.setOnMenuItemClickListener(item -> {
            String safeTitle = item.getTitle() != null ? item.getTitle().toString() : "";

            // This calls the second method (Fixes the "never used" error!)
            showOrderPopupMenu(anchor, txtCurrentSort, safeTitle, item.getItemId());
            return true;
        });
        popup.show();
    }

    private void showOrderPopupMenu(View anchor, TextView txtCurrentSort, String categoryName, int categoryId) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END);

        int ascId = 0, descId = 0;
        if (categoryId == 10) { ascId = 1; descId = 2; }       // Departure
        else if (categoryId == 20) { ascId = 3; descId = 4; }  // Arrival
        else if (categoryId == 30) { ascId = 5; descId = 6; }  // Destination
        else if (categoryId == 40) { ascId = 7; descId = 8; }  // Total Expense

        popup.getMenu().add(0, ascId, 0, "Ascending");
        popup.getMenu().add(0, descId, 0, "Descending");

        popup.setOnMenuItemClickListener(item -> {
            String safeTitle = item.getTitle() != null ? item.getTitle().toString() : "";

            // Figure out which arrow to use
            String arrow = safeTitle.equals("Ascending") ? "▲" : "▼";

            // Combine the category name and the arrow (Fixes the "Cannot resolve" error!)
            txtCurrentSort.setText(categoryName + " " + arrow);

            // Actually sort the list!
            applySorting(item.getItemId());
            return true;
        });
        popup.show();
    }

    private void applySorting(int sortOption) {
        currentSortOption = sortOption;

        tripList.sort((t1, t2) -> {
            // RULE 1: Pinned trips ALWAYS stay at the very top!
            if (t1.getIsPinnedState() != t2.getIsPinnedState()) {
                return Integer.compare(t2.getIsPinnedState(), t1.getIsPinnedState());
            }

            // RULE 2: Apply user sort choice
            try {
                switch (sortOption) {
                    case 1: return compareDates(t1.getStartDate(), t2.getStartDate(), true);
                    case 2: return compareDates(t1.getStartDate(), t2.getStartDate(), false);
                    case 3: return compareDates(t1.getEndDate(), t2.getEndDate(), true);
                    case 4: return compareDates(t1.getEndDate(), t2.getEndDate(), false);
                    case 5: return compareStrings(t1.getDestination(), t2.getDestination(), true);
                    case 6: return compareStrings(t1.getDestination(), t2.getDestination(), false);
                    // FIXED: 7 and 8 now correctly sort by Total Expenses!
                    case 7: return Double.compare(t1.getTotalExpenses(), t2.getTotalExpenses());
                    case 8: return Double.compare(t2.getTotalExpenses(), t1.getTotalExpenses());
                    default: return 0;
                }
            } catch (Exception e) {
                return 0; // Fallback in case of missing data
            }
        });
        adapter.notifyDataSetChanged();
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


    private void fetchTripsAndShowSelectionDialog(boolean isForExport) {
        buildAndShowDialog(tripList, isForExport);
    }

    private void buildAndShowDialog(ArrayList<TripModel> allTrips, boolean isForExport) {
        if (allTrips.isEmpty()) {
            Toast.makeText(this, "No trips available.", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_trips);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView txtTitle = dialog.findViewById(R.id.txt_dialog_title);
        if (txtTitle != null) {
            txtTitle.setText(isForExport ? R.string.title_select_trip_export : R.string.title_select_trip_transactions);
        }

        LinearLayout container = dialog.findViewById(R.id.container_dialog_trips);
        for (TripModel trip : allTrips) {
            View rowView = LayoutInflater.from(this).inflate(R.layout.item_dialog_trip, container, false);
            ((TextView) rowView.findViewById(R.id.txt_dialog_trip_name)).setText(trip.getTripName());
            ((TextView) rowView.findViewById(R.id.txt_dialog_trip_date)).setText(trip.getStartDate());

            rowView.setOnClickListener(v -> {
                dialog.dismiss();
                if (isForExport) {
                    selectedTripIdForExport = trip.getTripId();
                    String safeTripName = trip.getTripName().replaceAll("[^a-zA-Z0-9]", "_");
                    createMasterPdfLauncher.launch(safeTripName + "_Master_Ledger.pdf");
                } else {
                    Intent intent = new Intent(this, CompleteLedgerActivity.class);
                    intent.putExtra("TRIP_ID", trip.getTripId());
                    intent.putExtra("TRIP_NAME", trip.getTripName());
                    startActivity(intent);
                }
            });
            container.addView(rowView);
        }

        dialog.findViewById(R.id.btn_dialog_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void loadAndFilterTrips() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || account.getEmail() == null) {
            tripList.clear();
            adapter.notifyDataSetChanged();
            txtEmptyMessage.setVisibility(View.VISIBLE);
            return;
        }

        db.collection("Trips").whereEqualTo("ownerEmail", account.getEmail()).get()
                .addOnSuccessListener(querySnapshot -> {
                    tripList.clear();
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                    Date today = cal.getTime();

                    for (DocumentSnapshot doc : querySnapshot) {
                        String startStr = doc.getString("startDate") != null ? doc.getString("startDate") : "";
                        String endStr = doc.getString("endDate") != null ? doc.getString("endDate") : "";

                        boolean shouldAdd = false;
                        if (currentCategoryLabel.equalsIgnoreCase("All Trips")) {
                            shouldAdd = true;
                        } else {
                            try {
                                // Warning fix: Safe null and empty checking
                                Date sDate = (startStr != null && !startStr.isEmpty()) ? dateFormatter.parse(startStr) : null;
                                Date eDate = (endStr != null && !endStr.isEmpty()) ? dateFormatter.parse(endStr) : null;

                                if (currentCategoryLabel.equalsIgnoreCase("Upcoming Trips") && sDate != null && sDate.after(today)) {
                                    shouldAdd = true;
                                } else if (currentCategoryLabel.equalsIgnoreCase("Ongoing Trips") && sDate != null && eDate != null && !sDate.after(today) && !eDate.before(today)) {
                                    shouldAdd = true;
                                } else if (currentCategoryLabel.equalsIgnoreCase("Ended Trips") && eDate != null && eDate.before(today)) {
                                    shouldAdd = true;
                                }
                            } catch (ParseException ignored) {
                                shouldAdd = true;
                            }
                        }

                        if (shouldAdd) {
                            // Warning fix: Safe Long to int conversion
                            Long countLong = doc.getLong("memberCount");
                            int memberCount = (countLong != null) ? countLong.intValue() : 0;

                            TripModel trip = new TripModel(
                                    doc.getId(),
                                    doc.getString("tripName"),
                                    doc.getString("destination"),
                                    doc.getString("members"),
                                    memberCount,
                                    startStr,
                                    endStr
                            );

                            trip.setIsPinnedState(Boolean.TRUE.equals(doc.getBoolean("isPinned")) ? 1 : 0);
                            tripList.add(trip);

                            // Execute the background math
                            calculateTripFinance(trip);
                        }
                    }

                    txtEmptyMessage.setVisibility(tripList.isEmpty() ? View.VISIBLE : View.GONE);

                    // --- NEW: Trigger Sort instead of standard refresh ---
                    applySorting(currentSortOption);
                });
    }

    private void calculateTripFinance(TripModel trip) {
        TripFinanceCalculator.calculateFinances(trip.getTripId(), new TripFinanceCalculator.FinanceResultListener() {
            @Override
            public void onStart() {
                // Optional: You can update the UI or leave this empty
            }

            @Override
            public void onResult(double totalExp, double totalRec, double fundBal) {
                // Update your model data
                trip.setTotalExpenses(totalExp);
                trip.setTotalPayments(totalRec);
                trip.setFundBalance(fundBal);

                // --- CHANGED: Dynamically resort if sorting by Expense (IDs 7 or 8) ---
                if (currentSortOption == 7 || currentSortOption == 8) {
                    applySorting(currentSortOption);
                } else {
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }
    @SuppressWarnings("unused")
    private ArrayList<String> getHistoricalMembers(String tripId) {
        ArrayList<String> members = new ArrayList<>();
        String query = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ? UNION SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(query, new String[]{tripId, tripId})) {
            while (c.moveToNext()) {
                String name = c.getString(0).trim();
                if (!"Fund".equalsIgnoreCase(name) && !members.contains(name)) members.add(name);
            }
        }
        return members;
    }

    @Override
    public void onPinToggleClick(TripModel trip, int position) {
        boolean currentlyPinned = trip.getIsPinnedState() == 1;

        // If we are trying to PIN a new trip, we must unpin all others first
        if (!currentlyPinned) {
            for (int i = 0; i < tripList.size(); i++) {
                TripModel t = tripList.get(i);
                if (t.getIsPinnedState() == 1) {
                    t.setIsPinnedState(0);
                    // Update Cloud
                    db.collection("Trips").document(t.getTripId()).update("isPinned", false);
                }
            }
        }

        // Now, update the Cloud for the trip we actually clicked
        db.collection("Trips").document(trip.getTripId()).update("isPinned", !currentlyPinned)
                .addOnSuccessListener(a -> {
                    trip.setIsPinnedState(currentlyPinned ? 0 : 1);

                    // --- NEW: Force a re-sort so the pinned item jumps to top ---
                    applySorting(currentSortOption);

                    Toast.makeText(this, "Pin updated!", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDeleteClick(TripModel trip) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete '" + trip.getTripName() + "'?")
                .setPositiveButton("Yes, Delete", (dialog, which) ->
                        db.collection("Trips").document(trip.getTripId()).delete().addOnSuccessListener(a -> loadAndFilterTrips())
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onTripItemClick(TripModel trip) {
        Intent intent = new Intent(this, TripDetailsActivity.class);
        intent.putExtra("TRIP_ID", trip.getTripId());
        intent.putExtra("TRIP_NAME", trip.getTripName());
        intent.putExtra("DESTINATION", trip.getDestination());
        intent.putExtra("START_DATE", trip.getStartDate());
        intent.putExtra("MEMBERS", trip.getMembersListString());
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