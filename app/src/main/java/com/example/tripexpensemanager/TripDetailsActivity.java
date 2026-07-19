package com.example.tripexpensemanager;

import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;

// --- Firebase & Auth ---
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
//import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentReference;

@SuppressWarnings("deprecation")
public class TripDetailsActivity extends BaseDrawerActivity {

    private String tripId;
    private String startDateFromDatabase;
    private String endDateFromDatabase;
    private String tripName;
    private String tripOwnerEmail = ""; // NEW: Tracks who created the trip

    // --- CHANGED: Now holds complex TripMember objects! ---
    private final ArrayList<TripMember> currentMembersList = new ArrayList<>();
    private String rawLegacyMembers = "";

    private LedgerExportManager exportManager;
    private FirebaseFirestore db;
    private String currentUserRole = "Viewer";
    private boolean fabExpanded = false;

    private View viewDim;

    private ImageButton fabQuickActions;
    private LinearLayout layoutExpense;
    private LinearLayout layoutPayment;
    private LinearLayout layoutEdit;
    private LinearLayout layoutDelete;

    private final ActivityResultLauncher<String> createMasterPdfLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"),
            uri -> {
                if (uri != null && exportManager != null) {
                    ArrayList<String> membersList = new ArrayList<>();
                    for (TripMember m : currentMembersList) membersList.add(m.getMemberName());
                    exportManager.exportAllMembersToPdf(uri, tripId, membersList);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);

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
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        db = FirebaseFirestore.getInstance();
        TripDatabaseHelper dbHelper = new TripDatabaseHelper(this);
        exportManager = new LedgerExportManager(this, dbHelper);

        tripId = getIntent().getStringExtra("TRIP_ID");
        tripName = getIntent().getStringExtra("TRIP_NAME");
        String dest = getIntent().getStringExtra("DESTINATION");
        String date = getIntent().getStringExtra("START_DATE");
        startDateFromDatabase = getIntent().getStringExtra("START_DATE");
        endDateFromDatabase = getIntent().getStringExtra("END_DATE");

        // 1. Find the button
        LinearLayout btnManageAccess = findViewById(R.id.btn_manage_access);

        // 2. Set the click listener to open the dialog
        btnManageAccess.setOnClickListener(v -> {
            showManageAccessDialog(); // This calls the method we built together!
        });

        fabQuickActions = findViewById(R.id.fabQuickActions);

        MaterialCardView btnAddExpense = findViewById(R.id.btnAddExpense);
        MaterialCardView btnAddPayment = findViewById(R.id.btnAddPayment);
        MaterialCardView btnEditTrip = findViewById(R.id.btnEditTrip);
        MaterialCardView btnDeleteTrip = findViewById(R.id.btnDeleteTrip);
        MaterialCardView btnExportPdf = findViewById(R.id.btnallindividualtoonepdf);

        layoutExpense = findViewById(R.id.layoutExpense);
        layoutPayment = findViewById(R.id.layout_payment);
        layoutEdit = findViewById(R.id.layoutEdit);
        layoutDelete = findViewById(R.id.layoutDelete);
        LinearLayout layoutPdf = findViewById(R.id.layoutpdf);


        viewDim = findViewById(R.id.viewDim);

        hideButton(layoutExpense);
        hideButton(layoutPayment);
        hideButton(layoutEdit);
        hideButton(layoutDelete);
        hideButton(layoutPdf);

        fabQuickActions.setOnClickListener(v -> {

            if (fabExpanded)
                closeFabMenu();
            else
                openFabMenu();

        });

        viewDim.setOnClickListener(v -> closeFabMenu());


        btnAddExpense.setOnClickListener(v -> {
            closeFabMenu();
            if ("Viewer".equals(currentUserRole)) {
                Toast.makeText(this, "Only Admin and Editors can add expenses.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, AddExpenseActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_MEMBERS", this.rawLegacyMembers);
            startActivity(intent);
        });

        btnAddPayment.setOnClickListener(v -> {
            closeFabMenu();
            if ("Viewer".equals(currentUserRole)) {
                Toast.makeText(this, "Only Admin and Editors can add payments.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, AddPaymentActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_MEMBERS", this.rawLegacyMembers);
            startActivity(intent);
        });

        btnEditTrip.setOnClickListener(v -> {
            closeFabMenu();
            if (!"Admin".equals(currentUserRole)) {
                Toast.makeText(this, "Only the Admin can edit trips.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, UpdateTripActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("TRIP_NAME", tripName);
            intent.putExtra("TRIP_DESTINATION", ((TextView) findViewById(R.id.txt_details_destination)).getText().toString());
            intent.putExtra("TRIP_START_DATE", this.startDateFromDatabase);
            intent.putExtra("TRIP_END_DATE", this.endDateFromDatabase);
            intent.putExtra("TRIP_MEMBERS", this.rawLegacyMembers);
            startActivity(intent);
        });

        btnDeleteTrip.setOnClickListener(v -> {
            closeFabMenu();
            if (!"Admin".equals(currentUserRole)) {
                Toast.makeText(this, "Only the Admin can delete trips.", Toast.LENGTH_SHORT).show();
                return;
            }
            showDeleteDialog();
        });
        findViewById(R.id.btn_share_all).setOnClickListener(v -> exportManager.shareMasterPdf(tripId));

        btnExportPdf.setOnClickListener(v -> {
            closeFabMenu();
            String fileName = (tripName != null ? tripName.replaceAll("[^a-zA-Z0-9]", "_") : "Trip") + "_Master_Ledger.pdf";
            createMasterPdfLauncher.launch(fileName);
        });

        findViewById(R.id.btn_complete_ledger).setOnClickListener(v -> {
            Intent intent = new Intent(this, CompleteLedgerActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            startActivity(intent);
        });

        // 🟢 NEW: Setup SwipeRefreshLayout
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.parseColor("#85022E"));
            swipeRefreshLayout.setOnRefreshListener(this::refreshTripDetails); // Triggers your existing refresh method
        }

        // --- Navigation: Go to Dashboard (Home) ---
        LinearLayout btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(this, DashboardActivity.class);
                // Clears all other activities off the stack and brings the existing Dashboard to the front
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish(); // Closes the current TripDetailsActivity
            });
        }

        ((TextView) findViewById(R.id.txt_details_trip_name)).setText(getString(R.string.format_trip_name_header, tripName));
        ((TextView) findViewById(R.id.txt_details_destination)).setText(dest != null ? dest : "N/A");
        ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(date));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTripDetails();
    }

    @SuppressWarnings("unchecked")
    private void refreshTripDetails() {
        db.collection("Trips").document(tripId).get(Source.DEFAULT).addOnSuccessListener(doc -> {
            if (doc.exists()) {
                tripName = doc.getString("tripName");
                tripOwnerEmail = doc.getString("ownerEmail"); // Grab the Admin's email

                ((TextView) findViewById(R.id.txt_details_trip_name)).setText(getString(R.string.format_trip_name_header, tripName));
                ((TextView) findViewById(R.id.txt_details_destination)).setText(doc.getString("destination"));
                ((TextView) findViewById(R.id.txt_details_dates)).setText(formatDate(doc.getString("startDate")));

                startDateFromDatabase = doc.getString("startDate");
                endDateFromDatabase = doc.getString("endDate");

                String inactiveRaw = doc.getString("inactiveMembers");
                rawLegacyMembers = doc.getString("members");

                ImageButton btnArchiveTrip = findViewById(R.id.btn_archive_trip);

                btnArchiveTrip.setOnClickListener(v ->
                        new androidx.appcompat.app.AlertDialog.Builder(TripDetailsActivity.this)
                                .setTitle("Archive Trip")
                                .setMessage("Are you sure you want to archive this trip? (If you are Admin, Editors will be downgraded to Viewers globally)")
                                .setPositiveButton("Archive", (dialog, which) -> {

                                    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(TripDetailsActivity.this);
                                    String myEmail = (account != null && account.getEmail() != null) ? account.getEmail() : "";

                                    if (myEmail.isEmpty()) return;

                                    // 1. Initialize a WriteBatch to do everything atomically
                                    WriteBatch batch = db.batch();

                                    // 2. Personal Update: Add Trip ID to User's private array
                                    DocumentReference userRef = db.collection("Users").document(myEmail);
                                    java.util.Map<String, Object> userUpdate = new java.util.HashMap<>();
                                    userUpdate.put("archivedTripIds", FieldValue.arrayUnion(tripId));
                                    // Use merge() so it creates the user document if this is their first time archiving
                                    batch.set(userRef, userUpdate, SetOptions.merge());

                                    // 3. Global Update: Unpin and Downgrade roles
                                    DocumentReference tripRef = db.collection("Trips").document(tripId);
                                    java.util.Map<String, Object> firestoreUpdates = new java.util.HashMap<>();
                                    firestoreUpdates.put("isPinned", false);

                                    boolean tempRolesChanged = false;

                                    if ("Admin".equalsIgnoreCase(currentUserRole) && currentMembersList != null) {
                                        List<java.util.Map<String, Object>> updatedMemberDetails = new java.util.ArrayList<>();
                                        for (TripMember m : currentMembersList) {
                                            if ("Editor".equalsIgnoreCase(m.getRole())) {
                                                m.setRole("Viewer");
                                                tempRolesChanged = true;
                                            }
                                            java.util.Map<String, Object> map = new java.util.HashMap<>();
                                            map.put("memberName", m.getMemberName());
                                            map.put("emailId", m.getEmailId());
                                            map.put("role", m.getRole());
                                            updatedMemberDetails.add(map);
                                        }
                                        if (tempRolesChanged) {
                                            firestoreUpdates.put("memberDetails", updatedMemberDetails);
                                        }
                                    }

                                    final boolean rolesChangedFinal = tempRolesChanged;
                                    batch.update(tripRef, firestoreUpdates);

                                    // 4. Commit the batch
                                    batch.commit()
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(TripDetailsActivity.this,
                                                        rolesChangedFinal ? "Unpinned, Archived & Editors locked out." : "Trip Unpinned & Archived.",
                                                        Toast.LENGTH_SHORT).show();

                                                Intent intent = new Intent(TripDetailsActivity.this, DashboardActivity.class);
                                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                startActivity(intent);
                                                finish();
                                            })
                                            .addOnFailureListener(e ->Toast.makeText(TripDetailsActivity.this, "Network error. Could not archive.", Toast.LENGTH_SHORT).show());
                                })
                                .setNegativeButton("Cancel", null)
                                .show()
                );
                // --- Extract Rich Member Details ---
                currentMembersList.clear();
                List<Map<String, Object>> rawMemberDetails = (List<Map<String, Object>>) doc.get("memberDetails");

                if (rawMemberDetails != null && !rawMemberDetails.isEmpty()) {
                    for (Map<String, Object> map : rawMemberDetails) {
                        String mName = (String) map.get("memberName");
                        String mEmail = (String) map.get("emailId");
                        String mRole = (String) map.get("role");
                        currentMembersList.add(new TripMember(mName, mEmail, mRole));
                    }
                } else if (rawLegacyMembers != null && !rawLegacyMembers.isEmpty()) {
                    // Legacy Fallback
                    for (String name : rawLegacyMembers.split(",")) {
                        currentMembersList.add(new TripMember(name.trim(), null, null));
                    }
                }

                // --- Verify Admin to determine Role ---
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null && account.getEmail() != null) {
                    String myEmail = account.getEmail();
                    if (myEmail.equalsIgnoreCase(tripOwnerEmail)) {
                        currentUserRole = "Admin";
                    } else {
                        currentUserRole = "Viewer"; // Default
                        for (TripMember m : currentMembersList) {
                            if (m.getEmailId() != null && m.getEmailId().equalsIgnoreCase(myEmail)) {
                                currentUserRole = m.getRole() != null ? m.getRole() : "Viewer";
                                break;
                            }
                        }
                    }
                }

                // =======================================================
                // --- NEW CODE: UPDATE THE ROLE BADGE UI ---
                // =======================================================
                TextView txtRoleBadge = findViewById(R.id.txt_item_role_badge);
                if (txtRoleBadge != null) {
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
                }
                // =======================================================

                updateMemberGrids(inactiveRaw);
                refreshSummaryCards();
                androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        }).addOnFailureListener(e -> {
            Log.e("TripDetails", "Error refreshing trip details", e);
            androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            Toast.makeText(this, "Failed to refresh data", Toast.LENGTH_SHORT).show();
        });
    }

    // --- NEW: The Share Trip Dashboard Logic ---
    // --- NEW: The Share Trip Dashboard Logic ---
    private void showManageAccessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_access, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        LinearLayout layoutRows = view.findViewById(R.id.layout_access_rows);

        ArrayList<Spinner> roleSpinners = new ArrayList<>();
        ArrayList<TripMember> linkedMembers = new ArrayList<>();

        for (TripMember member : currentMembersList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout textContainer = new LinearLayout(this);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView txtName = new TextView(this);
            txtName.setText(member.getMemberName());
            txtName.setTextSize(16f);
            txtName.setTextColor(android.graphics.Color.BLACK);
            // --- FIX 1: Corrected method to set text to BOLD ---
            txtName.setTypeface(null, android.graphics.Typeface.BOLD);
            textContainer.addView(txtName);

            if (member.getEmailId() != null && !member.getEmailId().isEmpty()) {
                TextView txtEmail = new TextView(this);
                txtEmail.setText(member.getEmailId());
                txtEmail.setTextSize(12f);
                txtEmail.setTextColor(android.graphics.Color.DKGRAY);
                textContainer.addView(txtEmail);
                row.addView(textContainer);

                // --- FIX 2: Extracted Spinner Logic to a Helper Method ---
                Spinner spinner = createRoleSpinner(member.getRole());

                roleSpinners.add(spinner);
                linkedMembers.add(member);
                row.addView(spinner);

            } else {
                TextView txtNoEmail = new TextView(this);
                txtNoEmail.setText("No Email. Add email by editing Trip to assign role.");
                txtNoEmail.setTextSize(11f);
                txtNoEmail.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
                textContainer.addView(txtNoEmail);
                row.addView(textContainer);
            }

            layoutRows.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"));
            layoutRows.addView(divider);
        }

        view.findViewById(R.id.btn_dialog_close).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_dialog_save_access).setOnClickListener(v -> {
            // 1. Update the local list first
            for (int i = 0; i < roleSpinners.size(); i++) {
                String selected = roleSpinners.get(i).getSelectedItem().toString();
                String newRole = selected.equals("No Access") ? null : selected;
                linkedMembers.get(i).setRole(newRole);
            }

            // 2. Convert to Map for Firestore
            ArrayList<Map<String, Object>> memberDetailsList = new ArrayList<>();
            for (TripMember m : currentMembersList) {
                Map<String, Object> map = new HashMap<>();
                map.put("memberName", m.getMemberName());
                map.put("emailId", m.getEmailId());
                map.put("role", m.getRole());
                memberDetailsList.add(map);
            }

            // 3. Update Firestore and REFRESH immediately
            view.findViewById(R.id.btn_dialog_save_access).setEnabled(false);// Disable to prevent double-clicks
            db.collection("Trips").document(tripId)
                    .update("memberDetails", memberDetailsList)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Roles updated!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        // CRITICAL: Refresh the UI immediately so the buttons enable/disable
                        refreshTripDetails();
                    })
                    .addOnFailureListener(e -> {
                        view.findViewById(R.id.btn_dialog_save_access).setEnabled(true);
                        Toast.makeText(this, "Failed to save roles", Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();
    }

    // --- NEW: Helper Method to keep code clean and satisfy Android Studio ---
    private Spinner createRoleSpinner(String currentRole) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"No Access", "Viewer", "Editor"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if ("Editor".equalsIgnoreCase(currentRole)) spinner.setSelection(2);
        else if ("Viewer".equalsIgnoreCase(currentRole)) spinner.setSelection(1);
        else spinner.setSelection(0);

        return spinner;
    }

    private void updateMemberGrids(String inactiveRaw) {
        int count = currentMembersList.size();
        String displayText = count + (count == 1 ? " Person" : " Persons");
        ((TextView) findViewById(R.id.txt_details_member_count)).setText(displayText);

        GridLayout gridActive = findViewById(R.id.grid_members);
        gridActive.removeAllViews();
        for (TripMember m : currentMembersList) {
            addMemberButton(m.getMemberName(), gridActive);
        }

        GridLayout gridInactive = findViewById(R.id.grid_inactive_members);
        TextView txtInactiveHeader = findViewById(R.id.txt_inactive_members_header);

        ArrayList<String> inactiveMembers = new ArrayList<>();
        if (inactiveRaw != null && !inactiveRaw.isEmpty()) {
            inactiveMembers.addAll(Arrays.asList(inactiveRaw.split(",")));
        }

        if (!inactiveMembers.isEmpty()) {
            txtInactiveHeader.setVisibility(View.VISIBLE);
            gridInactive.setVisibility(View.VISIBLE);
            gridInactive.removeAllViews();
            for (String inactiveName : inactiveMembers) {
                addMemberButton(inactiveName.trim(), gridInactive);
            }
        } else {
            txtInactiveHeader.setVisibility(View.GONE);
            gridInactive.setVisibility(View.GONE);
        }
    }

    private void refreshSummaryCards() {
        TripFinanceCalculator.calculateFinances(tripId, new TripFinanceCalculator.FinanceResultListener() {
            @Override
            public void onStart() {
                TextView txtExpenses = findViewById(R.id.txt_details_total_expenses);
                if (txtExpenses != null) txtExpenses.setText("...");
            }

            @Override
            public void onResult(double totalExp, double totalRec, double fundBal) {
                TextView txtExpenses = findViewById(R.id.txt_details_total_expenses);
                TextView txtReceipts = findViewById(R.id.txt_details_total_receipts);
                TextView txtFundBalance = findViewById(R.id.txt_details_fund_balance);

                if (txtExpenses != null) txtExpenses.setText(formatCurrency(totalExp));
                if (txtReceipts != null) txtReceipts.setText(formatCurrency(totalRec));
                if (txtFundBalance != null) txtFundBalance.setText(formatCurrency(fundBal));
            }
        });
    }

    private String formatCurrency(double amount) {
        return String.format(Locale.US, "₹%.2f", amount);
    }

    private void addMemberButton(String mName, GridLayout grid) {
        Button btn = new Button(this);
        btn.setText(mName);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(this, MemberLedgerActivity.class);
            intent.putExtra("TRIP_ID", tripId);
            intent.putExtra("MEMBER_NAME", mName);
            startActivity(intent);
        });
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(8, 8, 8, 8);
        btn.setLayoutParams(params);
        grid.addView(btn);
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "N/A";
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yy", Locale.US);
        try {
            Date date = inputFormat.parse(dateStr);
            if (date != null) return outputFormat.format(date);
        } catch (ParseException e) {
            Log.e("TripDetails", "Error parsing date", e);
        }
        return dateStr;
    }

    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete this trip?")
                .setPositiveButton("Yes, Delete", (dialog, which) ->
                        db.collection("Trips").document(tripId).delete()
                                .addOnSuccessListener(a -> {
                                    Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                )
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void openFabMenu() {
        fabExpanded = true;

        viewDim.setVisibility(View.VISIBLE);
        viewDim.setAlpha(0f);
        viewDim.animate()
                .alpha(1f)
                .setDuration(200)
                .start();

        // Apply Blur Effect for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Replace 'main_content_container' with the ID of the view holding your background UI.
            // Do NOT use the root drawer layout, or the FABs will blur too!
            View contentToBlur = findViewById(R.id.main_content_container);
            if (contentToBlur != null) {
                contentToBlur.setRenderEffect(
                        RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP)
                );
            }
        }

        showButton(layoutExpense, 0);
        showButton(layoutPayment, 40);
        showButton(layoutEdit, 80);
        showButton(layoutDelete, 120);
        showButton(findViewById(R.id.layoutpdf), 160);

        fabQuickActions.animate()
                .rotation(45f)
                .setDuration(250)
                .start();
    }

    private void closeFabMenu() {
        fabExpanded = false;

        viewDim.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> viewDim.setVisibility(View.GONE))
                .start();

        // Remove Blur Effect for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            View contentToBlur = findViewById(R.id.main_content_container);
            if (contentToBlur != null) {
                contentToBlur.setRenderEffect(null);
            }
        }

        hideAnimated(findViewById(R.id.layoutpdf), 0);
        hideAnimated(layoutDelete, 30);
        hideAnimated(layoutEdit, 60);
        hideAnimated(layoutPayment, 90);
        hideAnimated(layoutExpense, 120);

        fabQuickActions.animate()
                .rotation(0f)
                .setDuration(250)
                .start();
    }
    private void showButton(View view,long delay){

        view.setVisibility(View.VISIBLE);

        view.setAlpha(0f);

        view.setTranslationY(80f);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(220)
                .start();

    }

    private void hideAnimated(View view,long delay){

        view.animate()
                .alpha(0f)
                .translationY(80f)
                .setStartDelay(delay)
                .setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {

                        view.setVisibility(View.GONE);

                        view.setAlpha(1f);

                        view.setTranslationY(0);

                        view.animate().setListener(null);

                    }
                }).start();

    }

    private void hideButton(View view){

        view.setVisibility(View.GONE);

        view.setAlpha(1f);

        view.setTranslationY(0);

    }
}