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
import android.widget.ImageButton;
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
import com.google.android.material.button.MaterialButton;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class TripListActivity extends AppCompatActivity implements TripAdapter.OnTripActionListener {

    private TextView txtEmptyMessage;
    private TripAdapter adapter;
    private final ArrayList<TripModel> tripList = new ArrayList<>();
    private String currentCategoryLabel;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

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

        // Warning fix: Kept as local variable since it's only used here
        ImageButton btnHeaderAddNewTrip = findViewById(R.id.btn_header_add_new_trip);

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

        ImageButton btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(TripListActivity.this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        MaterialButton btnExportAll = findViewById(R.id.unv_btn_all_individual_to_one_pdf);
        if (btnExportAll != null) {
            btnExportAll.setOnClickListener(v -> fetchTripsAndShowSelectionDialog(true));
        }

        MaterialButton btnCompleteLedger = findViewById(R.id.unv_btn_complete_ledger);
        if (btnCompleteLedger != null) {
            btnCompleteLedger.setOnClickListener(v -> fetchTripsAndShowSelectionDialog(false));
        }

        loadAndFilterTrips();
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
                    adapter.notifyDataSetChanged();
                });
    }

    private void calculateTripFinance(TripModel trip) {
        TripFinanceCalculator.calculateFinances(trip.getTripId(), (totalExp, totalRec, fundBal) -> {
            trip.setTotalExpenses(totalExp);
            trip.setTotalPayments(totalRec);
            trip.setFundBalance(fundBal);
            adapter.notifyDataSetChanged();
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
                    // Update UI for the old pinned trip
                    adapter.notifyItemChanged(i);
                }
            }
        }

        // Now, update the Cloud for the trip we actually clicked
        db.collection("Trips").document(trip.getTripId()).update("isPinned", !currentlyPinned)
                .addOnSuccessListener(a -> {
                    trip.setIsPinnedState(currentlyPinned ? 0 : 1);
                    adapter.notifyItemChanged(position);
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