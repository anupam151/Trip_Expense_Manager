package com.example.tripexpensemanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.button.MaterialButton;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

// 1. EXTEND BaseDrawerActivity
@SuppressWarnings("deprecation")
public class DashboardActivity extends BaseDrawerActivity {

    private TextView lblRecentHeading;
    private LinearLayout containerPinnedTripsStack, layoutNoPinnedTrips;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        db = FirebaseFirestore.getInstance();

        // 2. TELL THE BASE CLASS TO WIRE UP THE DRAWER!
        setupUniversalDrawer(R.id.drawer_layout, R.id.navigation_view);

        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        lblRecentHeading = findViewById(R.id.lbl_recent_trip_heading);
        containerPinnedTripsStack = findViewById(R.id.container_pinned_trips_stack);
        layoutNoPinnedTrips = findViewById(R.id.layout_no_pinned_trips);

        TextView txtDeveloperBranding = findViewById(R.id.txt_dash_developer_branding);
        String styledSignatureText = getString(R.string.dev_branding_signature_placeholder, "<b><font color='#1E88E5'>Anupam</font></b>");
        if (txtDeveloperBranding != null) {
            txtDeveloperBranding.setText(Html.fromHtml(styledSignatureText, Html.FROM_HTML_MODE_LEGACY));
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });

        btnOpenDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.btn_dash_create_trip).setOnClickListener(v -> launchCreateTripActivity());
        findViewById(R.id.btn_dash_view_trips).setOnClickListener(v -> launchTripListActivity());
        findViewById(R.id.btn_create_new_trips).setOnClickListener(v -> launchCreateTripActivity());

        // Add these lines inside your onCreate method:

        findViewById(R.id.btn_dash_utility).setOnClickListener(v ->startActivity(new Intent(this, UtilityActivity.class)));

        findViewById(R.id.btn_dash_settings).setOnClickListener(v ->startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSignInUI(); // (This method comes for free from BaseDrawerActivity!)
        fetchTripsFromCloud();
    }

    // 3. If a user logs in via the drawer, fetch their trips!
    @Override
    protected void onUserSuccessfullySignedIn(GoogleSignInAccount account) {
        fetchTripsFromCloud();
    }

    private void launchCreateTripActivity() {
        startActivity(new Intent(this, CreateTripActivity.class));
    }

    private void launchTripListActivity() {
        startActivity(new Intent(this, TripListActivity.class));
    }

    // --- Firebase Data Fetching ---
    // --- Firebase Data Fetching ---
    private void fetchTripsFromCloud() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || account.getEmail() == null) {
            clearWorkspace();
            return;
        }

        // --- CHANGED: Now looks for trips shared with this user! ---
        // 1. Try Server (Source.DEFAULT)
        db.collection("Trips")
                .whereArrayContains("sharedEmails", account.getEmail())
                .get(com.google.firebase.firestore.Source.DEFAULT)
                .addOnSuccessListener(this::processTripData)
                .addOnFailureListener(e -> {
                    // 2. If Server Fails (Offline), Fallback to Cache
                    db.collection("Trips")
                            .whereArrayContains("sharedEmails", account.getEmail())
                            .get(com.google.firebase.firestore.Source.CACHE)
                            .addOnSuccessListener(this::processTripData)
                            .addOnFailureListener(err -> {
                                // Truly no data found
                                clearWorkspace();
                            });
                });
    }

    private void processTripData(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots) {
        DocumentSnapshot pinnedTripDoc = null;
        for (DocumentSnapshot doc : queryDocumentSnapshots) {
            Object isPinnedObj = doc.get("isPinned");
            boolean isPinned = false;

            if (isPinnedObj instanceof Boolean) {
                isPinned = (Boolean) isPinnedObj;
            } else if (isPinnedObj instanceof Number) {
                isPinned = ((Number) isPinnedObj).intValue() == 1;
            } else if (isPinnedObj instanceof String) {
                isPinned = Boolean.parseBoolean((String) isPinnedObj) || "1".equals(isPinnedObj);
            }

            if (isPinned) {
                pinnedTripDoc = doc;
                break;
            }
        }

        if (pinnedTripDoc == null) {
            clearWorkspace();
        } else {
            populateWorkspace(pinnedTripDoc);
        }
    }

    @SuppressWarnings("unchecked")
    private void populateWorkspace(DocumentSnapshot doc) {
        containerPinnedTripsStack.removeAllViews();
        layoutNoPinnedTrips.setVisibility(View.GONE);
        containerPinnedTripsStack.setVisibility(View.VISIBLE);
        lblRecentHeading.setVisibility(View.VISIBLE);

        String tripId = doc.getId();
        String name = doc.getString("tripName") != null ? doc.getString("tripName") : "Unnamed Trip";
        String destination = doc.getString("destination") != null ? doc.getString("destination") : "Unknown";
        String members = doc.getString("members") != null ? doc.getString("members") : "";
        String startDate = doc.getString("startDate") != null ? doc.getString("startDate") : "";
        String endDate = doc.getString("endDate") != null ? doc.getString("endDate") : "";


        View cardView = LayoutInflater.from(this).inflate(R.layout.item_trip, containerPinnedTripsStack, false);

        TextView txtTripName = cardView.findViewById(R.id.txt_item_trip_name);
        TextView txtTotalExpense = cardView.findViewById(R.id.txt_item_total_expense);
        TextView txtTotalReceived = cardView.findViewById(R.id.txt_item_total_received);
        TextView txtFundBalance = cardView.findViewById(R.id.txt_item_fund_balance);
        TextView btnPin = cardView.findViewById(R.id.btn_item_pin);
        TextView btnEdit = cardView.findViewById(R.id.btn_item_edit);
        TextView btnDelete = cardView.findViewById(R.id.btn_item_delete);
        MaterialButton btnAddExpense = cardView.findViewById(R.id.btn_item_add_expense);
        MaterialButton btnAddPayment = cardView.findViewById(R.id.btn_item_add_payment);
        TextView txtRoleBadge = cardView.findViewById(R.id.txt_item_role_badge);

        txtTripName.setText(getString(R.string.fmt_dash_pinned_title, 1, destination));

        TripFinanceCalculator.calculateFinances(tripId, new TripFinanceCalculator.FinanceResultListener() {
            @Override
            public void onStart() {
                txtTotalExpense.setText("...");
                txtTotalReceived.setText("...");
                txtFundBalance.setText("...");
            }
            @Override
            public void onResult(double totalExp, double totalRec, double fundBal) {
                txtTotalExpense.setText(DashboardActivity.this.getString(R.string.fmt_dash_currency_rupees, totalExp));
                txtTotalReceived.setText(DashboardActivity.this.getString(R.string.fmt_dash_currency_rupees, totalRec));
                txtFundBalance.setText(String.format(Locale.US, "₹%.2f", fundBal));
            }
        });

        // =================================================================
        // --- ROLE CALCULATION ---
        // =================================================================

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        String currentUserEmail = (account != null && account.getEmail() != null) ? account.getEmail() : "";
        String currentUserRole = "Viewer"; // Safest default

        String ownerEmail = doc.getString("ownerEmail");
        if (!currentUserEmail.isEmpty()) {
            if (currentUserEmail.equalsIgnoreCase(ownerEmail)) {
                currentUserRole = "Admin";
            } else {
                java.util.List<java.util.Map<String, Object>> rawMemberDetails =
                        (java.util.List<java.util.Map<String, Object>>) doc.get("memberDetails");

                if (rawMemberDetails != null) {
                    for (java.util.Map<String, Object> memberMap : rawMemberDetails) {
                        String memberEmail = (String) memberMap.get("emailId");
                        if (currentUserEmail.equalsIgnoreCase(memberEmail)) {
                            String role = (String) memberMap.get("role");
                            currentUserRole = (role != null) ? role : "Viewer";
                            break;
                        }
                    }
                }
            }
        }

        final boolean isAdmin = "Admin".equalsIgnoreCase(currentUserRole);
        final boolean isEditor = "Editor".equalsIgnoreCase(currentUserRole);
        final boolean canAddTransactions = isAdmin || isEditor;
        txtRoleBadge.setText(currentUserRole);



        android.util.DisplayMetrics metrics = txtRoleBadge.getContext().getResources().getDisplayMetrics();

        int horizontalPadding = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 13, metrics);
        int verticalPadding = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 3, metrics);
        float elevationPx = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 4, metrics);
        float cornerRadiusPx = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 4, metrics);

        android.graphics.drawable.GradientDrawable backgroundShape = new android.graphics.drawable.GradientDrawable();
        backgroundShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        backgroundShape.setCornerRadius(cornerRadiusPx);

        txtRoleBadge.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );

        txtRoleBadge.setElevation(elevationPx);
        txtRoleBadge.setText(currentUserRole);

        if ("Admin".equalsIgnoreCase(currentUserRole)) {
            backgroundShape.setColor(0xFF85022E); // Blue
            txtRoleBadge.setTextColor(0xFFFAF7F7);
        } else if ("Editor".equalsIgnoreCase(currentUserRole)) {
            backgroundShape.setColor(0xFF3e8914); // Green
            txtRoleBadge.setTextColor(0xFFF5FFF6);
        } else {
            backgroundShape.setColor(0xFF2f4550); // Gray for Viewer
            txtRoleBadge.setTextColor(0xFFe9ecef);
        }

        txtRoleBadge.setBackground(backgroundShape);


        // =================================================================
        // --- CLICK LISTENERS WITH TOAST INTERCEPTS ---
        // =================================================================

        btnPin.setText(getString(R.string.action_unpin));
        btnPin.setTextColor(0xFF2E7D32);
        btnPin.setOnClickListener(v -> handlePinToggle(tripId, name));

        // Edit Button Logic
        btnEdit.setOnClickListener(v -> {
            if (isAdmin) {
                navigateToUpdateTrip(tripId, name, destination, members, startDate, endDate);
            } else {
                Toast.makeText(this, "Only the Admin can edit trips.", Toast.LENGTH_SHORT).show();
            }
        });

        // Delete Button Logic
        btnDelete.setOnClickListener(v -> {
            if (isAdmin) {
                showDeleteDialog(tripId, name);
            } else {
                Toast.makeText(this, "Only the Admin can delete trips.", Toast.LENGTH_SHORT).show();
            }
        });

        // Add Expense Button Logic
        btnAddExpense.setOnClickListener(v -> {
            if (canAddTransactions) {
                navigateToAddExpense(tripId, members);
            } else {
                Toast.makeText(this, "Only Admin and Editors can add expenses.", Toast.LENGTH_SHORT).show();
            }
        });

        // Add Payment Button Logic
        btnAddPayment.setOnClickListener(v -> {
            if (canAddTransactions) {
                navigateToAddPayment(tripId, members);
            } else {
                Toast.makeText(this, "Only Admin and Editors can add payments.", Toast.LENGTH_SHORT).show();
            }
        });

        cardView.setOnClickListener(v -> navigateToTripDetails(tripId, name, destination, members, startDate, endDate));

        containerPinnedTripsStack.addView(cardView);
    }

    private void clearWorkspace() {
        lblRecentHeading.setVisibility(View.GONE);
        containerPinnedTripsStack.setVisibility(View.GONE);
        layoutNoPinnedTrips.setVisibility(View.VISIBLE);
    }

    private void handlePinToggle(String tripId, String tripName) {
        db.collection("Trips").document(tripId).update("isPinned", false).addOnSuccessListener(aVoid -> {
            Toast.makeText(DashboardActivity.this, "'" + tripName + "' unpinned!", Toast.LENGTH_SHORT).show();
            fetchTripsFromCloud();
        });
    }

    private void showDeleteDialog(String tripId, String tripName) {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete '" + tripName + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Delete", (dialog, which) -> executeCloudDelete(tripId))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF85022E);
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF85022E);
    }

    private void executeCloudDelete(String tripId) {
        db.collection("Trips").document(tripId).delete().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Trip deleted!", Toast.LENGTH_SHORT).show();
            fetchTripsFromCloud();
        });
    }

    private void navigateToUpdateTrip(String tripId, String name, String destination, String members, String startDate, String endDate) {
        Intent intent = new Intent(this, UpdateTripActivity.class);
        intent.putExtra("TRIP_ID", tripId);
        intent.putExtra("TRIP_NAME", name);
        intent.putExtra("TRIP_DESTINATION", destination);
        intent.putExtra("TRIP_MEMBERS", members);
        intent.putExtra("TRIP_START_DATE", startDate);
        intent.putExtra("TRIP_END_DATE", endDate);
        startActivity(intent);
    }

    private void navigateToTripDetails(String tripId, String name, String destination, String members, String startDate, String endDate) {
        Intent intent = new Intent(this, TripDetailsActivity.class);
        intent.putExtra("TRIP_ID", tripId);
        intent.putExtra("TRIP_NAME", name);
        intent.putExtra("DESTINATION", destination);
        intent.putExtra("MEMBERS", members);
        intent.putExtra("START_DATE", startDate);
        intent.putExtra("END_DATE", endDate);
        startActivity(intent);
    }

    private void navigateToAddExpense(String tripId, String members) {
        Intent intent = new Intent(this, AddExpenseActivity.class);
        intent.putExtra("TRIP_ID", tripId);
        intent.putExtra("TRIP_MEMBERS", members);
        startActivity(intent);
    }

    private void navigateToAddPayment(String tripId, String members) {
        Intent intent = new Intent(this, AddPaymentActivity.class);
        intent.putExtra("TRIP_ID", tripId);
        intent.putExtra("TRIP_MEMBERS", members);
        startActivity(intent);
    }
}