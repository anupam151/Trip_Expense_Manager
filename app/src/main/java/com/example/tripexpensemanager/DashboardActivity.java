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

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

@SuppressWarnings("deprecation")
public class DashboardActivity extends BaseDrawerActivity {

    private TextView lblRecentHeading;
    private LinearLayout containerPinnedTripsStack, layoutNoPinnedTrips;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // --- START BIOMETRIC LOCK CHECK ---
        SharedPreferences prefs = getSharedPreferences("AppSettings", MODE_PRIVATE);
        boolean isLockEnabled = prefs.getBoolean("biometric_enabled", false);

        if (isLockEnabled) {
            findViewById(R.id.drawer_layout).setVisibility(View.INVISIBLE);
            requireAuthenticationToEnter();
        }
        // --- END BIOMETRIC LOCK CHECK ---

        db = FirebaseFirestore.getInstance();

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

        findViewById(R.id.btn_dash_utility).setOnClickListener(v -> startActivity(new Intent(this, UtilityActivity.class)));
        findViewById(R.id.btn_dash_settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSignInUI();
        fetchTripsFromCloud();
    }

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
    private void fetchTripsFromCloud() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || account.getEmail() == null) {
            clearWorkspace();
            return;
        }

        String userEmail = account.getEmail();

        // 1. Fetch user document to get personal private pinnedTripId
        db.collection("Users").document(userEmail).get()
                .addOnSuccessListener(userDoc -> {
                    String pinnedTripId = userDoc.exists() ? userDoc.getString("pinnedTripId") : null;

                    // 2. Fetch Trips shared with this user
                    db.collection("Trips")
                            .whereArrayContains("sharedEmails", userEmail)
                            .get(com.google.firebase.firestore.Source.DEFAULT)
                            .addOnSuccessListener(snapshot -> processTripData(snapshot, pinnedTripId))
                            .addOnFailureListener(e -> db.collection("Trips")
                                    .whereArrayContains("sharedEmails", userEmail)
                                    .get(com.google.firebase.firestore.Source.CACHE)
                                    .addOnSuccessListener(snapshot -> processTripData(snapshot, pinnedTripId))
                                    .addOnFailureListener(err -> clearWorkspace()));
                })
                .addOnFailureListener(e -> clearWorkspace());
    }

    private void processTripData(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots, String pinnedTripId) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        String currentUserEmail = (account != null && account.getEmail() != null) ? account.getEmail() : "";

        DocumentSnapshot pinnedTripDoc = null;
        for (DocumentSnapshot doc : queryDocumentSnapshots) {

            // ==========================================
            // SECURITY CHECK: DOES THIS USER STILL HAVE ACCESS?
            // ==========================================
            String ownerEmail = doc.getString("ownerEmail");
            boolean hasAccess = false;

            if (currentUserEmail.equalsIgnoreCase(ownerEmail)) {
                hasAccess = true;
            } else {
                Object rawData = doc.get("memberDetails");
                if (rawData instanceof java.util.List) {
                    for (Object item : (java.util.List<?>) rawData) {
                        if (item instanceof java.util.Map) {
                            java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                            String memberEmail = (String) map.get("emailId");

                            if (currentUserEmail.equalsIgnoreCase(memberEmail)) {
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
            // ==========================================

            if (pinnedTripId != null && pinnedTripId.equals(doc.getId())) {
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
        String currentUserRole = "Viewer";

        String ownerEmail = doc.getString("ownerEmail");
        if (!currentUserEmail.isEmpty()) {
            if (currentUserEmail.equalsIgnoreCase(ownerEmail)) {
                currentUserRole = "Admin";
            } else {
                Object rawData = doc.get("memberDetails");
                if (rawData instanceof java.util.List) {
                    for (Object item : (java.util.List<?>) rawData) {
                        if (item instanceof java.util.Map) {
                            java.util.Map<?, ?> map = (java.util.Map<?, ?>) item;
                            String memberEmail = (String) map.get("emailId");

                            if (currentUserEmail.equalsIgnoreCase(memberEmail)) {
                                String role = (String) map.get("role");
                                currentUserRole = (role != null) ? role : "Viewer";
                                break;
                            }
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

        txtRoleBadge.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        txtRoleBadge.setElevation(elevationPx);
        txtRoleBadge.setText(currentUserRole);

        if ("Admin".equalsIgnoreCase(currentUserRole)) {
            backgroundShape.setColor(0xFF85022E);
            txtRoleBadge.setTextColor(0xFFFAF7F7);
        } else if ("Editor".equalsIgnoreCase(currentUserRole)) {
            backgroundShape.setColor(0xFF3e8914);
            txtRoleBadge.setTextColor(0xFFF5FFF6);
        } else {
            backgroundShape.setColor(0xFF2f4550);
            txtRoleBadge.setTextColor(0xFFe9ecef);
        }

        txtRoleBadge.setBackground(backgroundShape);

        // =================================================================
        // --- CLICK LISTENERS WITH TOAST INTERCEPTS ---
        // =================================================================

        btnPin.setText(getString(R.string.action_unpin));
        btnPin.setTextColor(0xFF2E7D32);
        btnPin.setOnClickListener(v -> handlePinToggle(name));

        btnEdit.setOnClickListener(v -> {
            if (isAdmin) {
                navigateToUpdateTrip(tripId, name, destination, members, startDate, endDate);
            } else {
                Toast.makeText(this, "Only the Admin can edit trips.", Toast.LENGTH_SHORT).show();
            }
        });

        btnDelete.setOnClickListener(v -> {
            if (isAdmin) {
                showDeleteDialog(tripId, name);
            } else {
                Toast.makeText(this, "Only the Admin can delete trips.", Toast.LENGTH_SHORT).show();
            }
        });

        btnAddExpense.setOnClickListener(v -> {
            if (canAddTransactions) {
                navigateToAddExpense(tripId, members);
            } else {
                Toast.makeText(this, "Only Admin and Editors can add expenses.", Toast.LENGTH_SHORT).show();
            }
        });

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

    private void handlePinToggle(String tripName) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || account.getEmail() == null) return;
        String userEmail = account.getEmail();

        db.collection("Users").document(userEmail).update("pinnedTripId", null)
                .addOnSuccessListener(aVoid -> {
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

    // =================================================================
    // --- BIOMETRIC APP LOCK LOGIC ---
    // =================================================================
    private void requireAuthenticationToEnter() {
        BiometricPrompt biometricPrompt = createBiometricPrompt();

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("App Locked")
                .setSubtitle("Verify your identity to open Trip Expense Manager")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private BiometricPrompt createBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt.AuthenticationCallback authCallback = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                findViewById(R.id.drawer_layout).setVisibility(View.VISIBLE);
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                finish();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        };

        return new BiometricPrompt(this, executor, authCallback);
    }
}