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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.GravityCompat;

@SuppressWarnings("deprecation")
public class TripListActivity extends BaseDrawerActivity implements TripAdapter.OnTripActionListener {

    private TextView txtEmptyMessage;
    private TripAdapter adapter;
    private final ArrayList<TripModel> tripList = new ArrayList<>();
    private String currentCategoryLabel;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private int currentSortOption = 1; // Default to Departure Ascending

    private FirebaseFirestore db;
    private TripDatabaseHelper dbHelper;
    private LedgerExportManager exportManager;
    private String selectedTripIdForExport = null;

    private final ArrayList<TripModel> masterTripList = new ArrayList<>();
    private String currentSearchQuery = "";

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
        setupUniversalDrawer(R.id.drawer_layout, R.id.navigation_view);

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

        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_view_trips);
        txtEmptyMessage = findViewById(R.id.txt_empty_trips_message);
        TextView txtCategoryTitle = findViewById(R.id.txt_trip_list_category_title);

        LinearLayout btnHeaderAddNewTrip = findViewById(R.id.btn_header_add_new_trip);

        LinearLayout btnSortDropdown = findViewById(R.id.btn_sort_dropdown);
        TextView txtCurrentSort = findViewById(R.id.txt_current_sort);

        if (btnSortDropdown != null && txtCurrentSort != null) {
            txtCurrentSort.setText("Departure ▲");
            btnSortDropdown.setOnClickListener(v -> showSortPopupMenu(v, txtCurrentSort));
        }

        db = FirebaseFirestore.getInstance();
        dbHelper = new TripDatabaseHelper(this);
        exportManager = new LedgerExportManager(this, dbHelper);

        if (getIntent() != null && getIntent().getStringExtra("CATEGORY_TITLE") != null) {
            currentCategoryLabel = getIntent().getStringExtra("CATEGORY_TITLE");
        } else {
            currentCategoryLabel = "All Trips";
        }

        txtCategoryTitle.setText(currentCategoryLabel);
        txtEmptyMessage.setText(getString(R.string.msg_no_trip_added));

        EditText editSearchTrips = findViewById(R.id.edit_search_trips);
        if (editSearchTrips != null) {
            editSearchTrips.addTextChangedListener(new TextWatcher() {
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
        }

        if (editSearchTrips != null) {
            editSearchTrips.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    editSearchTrips.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    return true;
                }
                return false;
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(tripList, true, this);
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

        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#85022E"));
            swipeRefreshLayout.setOnRefreshListener(this::loadAndFilterTrips);
        }
    }

    private void showSortPopupMenu(View anchor, TextView txtCurrentSort) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchor, android.view.Gravity.END);

        popup.getMenu().add(0, 10, 0, "Departure");
        popup.getMenu().add(0, 20, 0, "Arrival");
        popup.getMenu().add(0, 30, 0, "Destination");
        popup.getMenu().add(0, 40, 0, "Total Expense");

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
        else if (categoryId == 20) { ascId = 3; descId = 4; }  // Arrival
        else if (categoryId == 30) { ascId = 5; descId = 6; }  // Destination
        else if (categoryId == 40) { ascId = 7; descId = 8; }  // Total Expense

        popup.getMenu().add(0, ascId, 0, "Ascending");
        popup.getMenu().add(0, descId, 0, "Descending");

        popup.setOnMenuItemClickListener(item -> {
            String safeTitle = item.getTitle() != null ? item.getTitle().toString() : "";
            String arrow = safeTitle.equals("Ascending") ? "▲" : "▼";
            txtCurrentSort.setText(categoryName + " " + arrow);
            applySorting(item.getItemId());
            return true;
        });
        popup.show();
    }

    private void applySorting(int sortOption) {
        currentSortOption = sortOption;
        applyFilterAndSort();
        tripList.sort((t1, t2) -> {
            if (t1.getIsPinnedState() != t2.getIsPinnedState()) {
                return Integer.compare(t2.getIsPinnedState(), t1.getIsPinnedState());
            }

            try {
                switch (sortOption) {
                    case 1: return compareDates(t1.getStartDate(), t2.getStartDate(), true);
                    case 2: return compareDates(t1.getStartDate(), t2.getStartDate(), false);
                    case 3: return compareDates(t1.getEndDate(), t2.getEndDate(), true);
                    case 4: return compareDates(t1.getEndDate(), t2.getEndDate(), false);
                    case 5: return compareStrings(t1.getDestination(), t2.getDestination(), true);
                    case 6: return compareStrings(t1.getDestination(), t2.getDestination(), false);
                    case 7: return Double.compare(t1.getTotalExpenses(), t2.getTotalExpenses());
                    case 8: return Double.compare(t2.getTotalExpenses(), t1.getTotalExpenses());
                    default: return 0;
                }
            } catch (Exception e) {
                return 0;
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

        String userEmail = account.getEmail();

        // 1. Fetch user document to get personal archived IDs and private pinnedTripId
        db.collection("Users").document(userEmail).get()
                .addOnSuccessListener(userDoc -> {

                    List<String> cloudArchivedIds = new ArrayList<>();
                    String userPinnedTripId = null;

                    if (userDoc.exists()) {
                        if (userDoc.contains("archivedTripIds")) {
                            Object rawList = userDoc.get("archivedTripIds");
                            if (rawList instanceof List<?>) {
                                for (Object item : (List<?>) rawList) {
                                    if (item instanceof String) {
                                        cloudArchivedIds.add((String) item);
                                    }
                                }
                            }
                        }
                        userPinnedTripId = userDoc.getString("pinnedTripId");
                    }

                    final String finalPinnedTripId = userPinnedTripId;

                    // 2. Fetch ALL trips shared with this user
                    db.collection("Trips").whereArrayContains("sharedEmails", userEmail).get()
                            .addOnSuccessListener(querySnapshot -> {
                                masterTripList.clear();

                                Calendar cal = Calendar.getInstance();
                                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                                Date today = cal.getTime();

                                for (DocumentSnapshot doc : querySnapshot) {

                                    if (cloudArchivedIds.contains(doc.getId())) {
                                        continue;
                                    }

                                    // SECURITY CHECK: DOES THIS USER STILL HAVE ACCESS?
                                    String ownerEmail = doc.getString("ownerEmail");
                                    boolean hasAccess = false;

                                    if (userEmail.equalsIgnoreCase(ownerEmail)) {
                                        hasAccess = true;
                                    } else {
                                        Object rawData = doc.get("memberDetails");
                                        if (rawData instanceof List) {
                                            for (Object item : (List<?>) rawData) {
                                                if (item instanceof Map) {
                                                    Map<?, ?> map = (Map<?, ?>) item;
                                                    String memberEmail = (String) map.get("emailId");

                                                    if (userEmail.equalsIgnoreCase(memberEmail)) {
                                                        String role = (String) map.get("role");

                                                        if (role == null || !role.trim().equalsIgnoreCase("No Access")) {
                                                            hasAccess = true;
                                                        }
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (!hasAccess) {
                                        continue;
                                    }

                                    String startStr = doc.getString("startDate") != null ? doc.getString("startDate") : "";
                                    String endStr = doc.getString("endDate") != null ? doc.getString("endDate") : "";

                                    boolean shouldAdd = false;
                                    if (currentCategoryLabel.equalsIgnoreCase("All Trips")) {
                                        shouldAdd = true;
                                    } else {
                                        try {
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

                                        // Set pin state based on private user setting instead of global document
                                        boolean isPinnedLocal = finalPinnedTripId != null && finalPinnedTripId.equals(doc.getId());
                                        trip.setIsPinnedState(isPinnedLocal ? 1 : 0);

                                        trip.setInactiveMembers(doc.getString("inactiveMembers"));
                                        trip.setOwnerEmail(doc.getString("ownerEmail"));

                                        Object rawData = doc.get("memberDetails");
                                        if (rawData instanceof List) {
                                            ArrayList<TripMember> parsedMembers = new ArrayList<>();
                                            for (Object item : (List<?>) rawData) {
                                                if (item instanceof Map) {
                                                    Map<?, ?> map = (Map<?, ?>) item;
                                                    parsedMembers.add(new TripMember(
                                                            (String) map.get("memberName"),
                                                            (String) map.get("emailId"),
                                                            (String) map.get("role")
                                                    ));
                                                }
                                            }
                                            trip.setMemberDetails(parsedMembers);
                                        }

                                        masterTripList.add(trip);
                                        calculateTripFinance(trip);
                                    }
                                }
                                applyFilterAndSort();
                                txtEmptyMessage.setVisibility(tripList.isEmpty() ? View.VISIBLE : View.GONE);

                                applySorting(currentSortOption);
                                androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipe_refresh_layout);
                                if (swipeRefresh != null) {
                                    swipeRefresh.setRefreshing(false);
                                }
                            })
                            .addOnFailureListener(e -> {
                                androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipe_refresh_layout);
                                if (swipeRefresh != null) {
                                    swipeRefresh.setRefreshing(false);
                                }
                                Toast.makeText(this, "Failed to load trips.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipe_refresh_layout);
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                    Toast.makeText(this, "Failed to check user status.", Toast.LENGTH_SHORT).show();
                });
    }

    private void calculateTripFinance(TripModel trip) {
        TripFinanceCalculator.calculateFinances(trip.getTripId(), new TripFinanceCalculator.FinanceResultListener() {
            @Override
            public void onStart() {}

            @Override
            public void onResult(double totalExp, double totalRec, double fundBal) {
                trip.setTotalExpenses(totalExp);
                trip.setTotalPayments(totalRec);
                trip.setFundBalance(fundBal);

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

    private void applyFilterAndSort() {
        tripList.clear();

        if (currentSearchQuery.isEmpty()) {
            tripList.addAll(masterTripList);
        } else {
            for (TripModel trip : masterTripList) {
                String name = trip.getTripName() != null ? trip.getTripName().toLowerCase() : "";
                String dest = trip.getDestination() != null ? trip.getDestination().toLowerCase() : "";
                String members = trip.getMembersListString() != null ? trip.getMembersListString().toLowerCase() : "";
                String inactive = trip.getInactiveMembers() != null ? trip.getInactiveMembers().toLowerCase() : "";

                if (name.contains(currentSearchQuery) ||
                        dest.contains(currentSearchQuery) ||
                        members.contains(currentSearchQuery) ||
                        inactive.contains(currentSearchQuery)) {
                    tripList.add(trip);
                }
            }
        }

        tripList.sort((t1, t2) -> {
            if (t1.getIsPinnedState() != t2.getIsPinnedState()) {
                return Integer.compare(t2.getIsPinnedState(), t1.getIsPinnedState());
            }
            try {
                switch (currentSortOption) {
                    case 1: return compareDates(t1.getStartDate(), t2.getStartDate(), true);
                    case 2: return compareDates(t1.getStartDate(), t2.getStartDate(), false);
                    case 3: return compareDates(t1.getEndDate(), t2.getEndDate(), true);
                    case 4: return compareDates(t1.getEndDate(), t2.getEndDate(), false);
                    case 5: return compareStrings(t1.getDestination(), t2.getDestination(), true);
                    case 6: return compareStrings(t1.getDestination(), t2.getDestination(), false);
                    case 7: return Double.compare(t1.getTotalExpenses(), t2.getTotalExpenses());
                    case 8: return Double.compare(t2.getTotalExpenses(), t1.getTotalExpenses());
                    default: return 0;
                }
            } catch (Exception e) {
                return 0;
            }
        });

        adapter.notifyDataSetChanged();
        txtEmptyMessage.setVisibility(tripList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPinToggleClick(TripModel trip, int position) {
        boolean currentlyPinned = trip.getIsPinnedState() == 1;
        String myEmail = getCurrentUserEmail();
        if (myEmail.isEmpty()) return;

        com.google.firebase.firestore.DocumentReference userRef = db.collection("Users").document(myEmail);
        Map<String, Object> update = new HashMap<>();

        // If currently pinned, clear it (null). Otherwise, assign this trip ID privately.
        update.put("pinnedTripId", currentlyPinned ? null : trip.getTripId());

        userRef.set(update, SetOptions.merge())
                .addOnSuccessListener(a -> {
                    loadAndFilterTrips();
                    Toast.makeText(this, currentlyPinned ? "Trip unpinned!" : "Trip pinned!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update pin: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDeleteClick(TripModel trip) {
        String myEmail = getCurrentUserEmail();
        if (!"Admin".equals(trip.getCurrentUserRole(myEmail))) {
            Toast.makeText(this, "Only the Admin can delete trips.", Toast.LENGTH_SHORT).show();
            return;
        }
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
        String myEmail = getCurrentUserEmail();
        if (!"Admin".equals(trip.getCurrentUserRole(myEmail))) {
            Toast.makeText(this, "Only the Admin can edit trips.", Toast.LENGTH_SHORT).show();
            return;
        }
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
        String myEmail = getCurrentUserEmail();
        if ("Viewer".equals(trip.getCurrentUserRole(myEmail))) {
            Toast.makeText(this, "Only Admin and Editors can add expenses.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, AddExpenseActivity.class);
        intent.putExtra("TRIP_ID", trip.getTripId());
        intent.putExtra("TRIP_MEMBERS", trip.getMembersListString());
        startActivity(intent);
    }

    @Override
    public void onAddPaymentClick(TripModel trip) {
        String myEmail = getCurrentUserEmail();
        if ("Viewer".equals(trip.getCurrentUserRole(myEmail))) {
            Toast.makeText(this, "Only Admin and Editors can add payments.", Toast.LENGTH_SHORT).show();
            return;
        }
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

    private String getCurrentUserEmail() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            return account.getEmail();
        }
        return "";
    }
}