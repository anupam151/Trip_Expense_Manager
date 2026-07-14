package com.example.tripexpensemanager;

import android.content.Intent;
//import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
//import android.text.SpannableString;
//import android.text.style.ForegroundColorSpan;
//import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
//import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;

// --- Firebase Imports ---
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

@SuppressWarnings("deprecation")
public class DashboardActivity extends AppCompatActivity {

    private TextView lblRecentHeading;
    private LinearLayout containerPinnedTripsStack, layoutNoPinnedTrips;
    private DrawerLayout drawerLayout;
    private NavigationView navView;

    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;

    // Converted to an expression lambda using a method reference to avoid warnings
    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::handleSignInResult);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        db = FirebaseFirestore.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        drawerLayout = findViewById(R.id.drawer_layout);
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        navView = findViewById(R.id.navigation_view);
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

        // Expression lambdas!
        btnOpenDrawer.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.btn_dash_create_trip).setOnClickListener(v -> launchCreateTripActivity());
        findViewById(R.id.btn_dash_view_trips).setOnClickListener(v -> launchTripListActivity());
        findViewById(R.id.btn_create_new_trips).setOnClickListener(v -> launchCreateTripActivity());

        navView.setNavigationItemSelectedListener(this::handleNavigationItemSelected);
        // ----------------------------------------------------

        updateSignInUI();
    }
    @Override
    protected void onResume() {
        super.onResume();
        updateSignInUI();
        fetchTripsFromCloud();
    }
    // --- Extracted logic to fix block lambda warnings ---
    private void handleSignInResult(androidx.activity.result.ActivityResult result) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            Toast.makeText(this, "Signed in as: " + account.getEmail(), Toast.LENGTH_SHORT).show();
            updateSignInUI();
            fetchTripsFromCloud();
        } catch (ApiException e) {
            Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show();
        }
    }
    private boolean handleNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        // CHANGE IS HERE: Updated to nav_login_toggle
        if (id == R.id.nav_login_toggle) {
            if (GoogleSignIn.getLastSignedInAccount(this) != null) {
                signOut();
            } else {
                signInWithGoogle();
            }
        } else if (id == R.id.nav_create_trip) {
            launchCreateTripActivity();
        } else if (id == R.id.nav_view_trips) {
            launchTripListActivity();
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (id == R.id.nav_backup || id == R.id.nav_restore) {
            Toast.makeText(this, "Auto-Sync is active! Your data is safe in the Cloud.", Toast.LENGTH_LONG).show();
        }

        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        return true;
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
        if (account == null) {
            clearWorkspace();
            return;
        }

        // 1. Try Server (Source.DEFAULT)
        db.collection("Trips")
                .whereEqualTo("ownerEmail", account.getEmail())
                .get(com.google.firebase.firestore.Source.DEFAULT)
                .addOnSuccessListener(this::processTripData)
                .addOnFailureListener(e -> {
                    // 2. If Server Fails (Offline), Fallback to Cache
                    db.collection("Trips")
                            .whereEqualTo("ownerEmail", account.getEmail())
                            .get(com.google.firebase.firestore.Source.CACHE)
                            .addOnSuccessListener(this::processTripData)
                            .addOnFailureListener(err -> {
                                // Truly no data found
                                clearWorkspace();
                            });
                });
    }

    // 3. Helper Method to process the results (The logic you already had)
    private void processTripData(com.google.firebase.firestore.QuerySnapshot queryDocumentSnapshots) {
        DocumentSnapshot pinnedTripDoc = null;

        // Safely find the pinned trip
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

    // --- FIX: Modified to accept a single DocumentSnapshot ---
    private void populateWorkspace(DocumentSnapshot doc) {
        // 1. Prepare the UI
        containerPinnedTripsStack.removeAllViews();
        layoutNoPinnedTrips.setVisibility(View.GONE);
        containerPinnedTripsStack.setVisibility(View.VISIBLE);
        lblRecentHeading.setVisibility(View.VISIBLE);

        // 2. Extract Data Safely
        String tripId = doc.getId();
        String name = doc.getString("tripName") != null ? doc.getString("tripName") : "Unnamed Trip";
        String destination = doc.getString("destination") != null ? doc.getString("destination") : "Unknown";
        String members = doc.getString("members") != null ? doc.getString("members") : "";
        String startDate = doc.getString("startDate") != null ? doc.getString("startDate") : "";
        String endDate = doc.getString("endDate") != null ? doc.getString("endDate") : "";

        Long countLong = doc.getLong("memberCount");
        int count = (countLong != null) ? countLong.intValue() : 1;

        // 3. Inflate the Card
        View cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_trip, containerPinnedTripsStack, false);

        TextView txtTripName = cardView.findViewById(R.id.txt_item_trip_name);
        TextView txtDestination = cardView.findViewById(R.id.txt_item_destination);
        TextView txtMemberCount = cardView.findViewById(R.id.txt_item_member_count);
        TextView txtStartDate = cardView.findViewById(R.id.txt_item_start_date);
        TextView txtTotalExpense = cardView.findViewById(R.id.txt_item_total_expense);
        TextView txtTotalReceived = cardView.findViewById(R.id.txt_item_total_received);
        TextView txtFundBalance = cardView.findViewById(R.id.txt_item_fund_balance);

        // Buttons
        TextView btnPin = cardView.findViewById(R.id.btn_item_pin);
        TextView btnEdit = cardView.findViewById(R.id.btn_item_edit);
        TextView btnDelete = cardView.findViewById(R.id.btn_item_delete);
        MaterialButton btnAddExpense = cardView.findViewById(R.id.btn_item_add_expense);
        MaterialButton btnAddPayment = cardView.findViewById(R.id.btn_item_add_payment);

        // 4. Bind static data
        txtTripName.setText(getString(R.string.fmt_dash_pinned_title, 1, name));
        txtDestination.setText(getString(R.string.fmt_dash_destination, destination));
        txtMemberCount.setText(getString(R.string.fmt_dash_member_count, count));
        txtStartDate.setText(getString(R.string.fmt_dash_start_date, startDate));

        // 5. THE "UNIVERSAL BRAIN" CALL
        // --- THE UNIVERSAL BRAIN CALL ---
        TripFinanceCalculator.calculateFinances(tripId, new TripFinanceCalculator.FinanceResultListener() {
            @Override
            public void onStart() {
                // Optional: Clear text so user doesn't see "0"
                txtTotalExpense.setText("...");
                txtTotalReceived.setText("...");
                txtFundBalance.setText("...");
            }

            @Override
            public void onResult(double totalExp, double totalRec, double fundBal) {
                // No need for (Double) cast - Java handles this automatically!
                txtTotalExpense.setText(DashboardActivity.this.getString(R.string.fmt_dash_currency_rupees, totalExp));
                txtTotalReceived.setText(DashboardActivity.this.getString(R.string.fmt_dash_currency_rupees, totalRec));
                txtFundBalance.setText(String.format(Locale.US, "₹%.2f", fundBal));
            }
        });

        // 6. Set Listeners
        btnPin.setText(getString(R.string.action_unpin));
        btnPin.setTextColor(0xFF2E7D32);
        btnPin.setOnClickListener(v -> handlePinToggle(tripId, name));
        btnEdit.setOnClickListener(v -> navigateToUpdateTrip(tripId, name, destination, members, startDate, endDate));
        btnDelete.setOnClickListener(v -> showDeleteDialog(tripId, name));
        btnAddExpense.setOnClickListener(v -> navigateToAddExpense(tripId, members));
        btnAddPayment.setOnClickListener(v -> navigateToAddPayment(tripId, members));
        cardView.setOnClickListener(v -> navigateToTripDetails(tripId, name, destination, members, startDate, endDate));

        // 7. Add to UI
        containerPinnedTripsStack.addView(cardView);
    }

    private void clearWorkspace() {
        lblRecentHeading.setVisibility(View.GONE);
        containerPinnedTripsStack.setVisibility(View.GONE);
        layoutNoPinnedTrips.setVisibility(View.VISIBLE);
    }

    // --- Extracted Helper Methods for Button Clicks ---
    private void handlePinToggle(String tripId, String tripName) {
        db.collection("Trips")
                .document(tripId)
                .update("isPinned", false)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(
                            DashboardActivity.this,
                            "'" + tripName + "' unpinned!",
                            Toast.LENGTH_SHORT
                    ).show();

                    fetchTripsFromCloud();
                });
    }

    private void showDeleteDialog(String tripId, String tripName) {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete '" + tripName + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Delete", (dialog, which) -> executeCloudDelete(tripId)) // Expression lambda for dialog!
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF85022E);
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF85022E);
    }

    private void executeCloudDelete(String tripId) {
        db.collection("Trips").document(tripId).delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Trip deleted from Cloud", Toast.LENGTH_SHORT).show();
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

    // --- Authentication & UI Methods ---
    private void updateSignInUI() {
        if (navView == null) return;
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);

        // 1. Update the Header (Profile Info)
        android.view.View headerView = navView.getHeaderView(0);
        if (headerView != null) {
            TextView txtTitle = headerView.findViewById(R.id.txt_login_title);
            TextView txtDesc = headerView.findViewById(R.id.txt_login_desc);
            android.widget.ImageView imgProfile = headerView.findViewById(R.id.img_profile_picture);

            if (account != null) {
                if (txtTitle != null) {
                    String fullName = account.getDisplayName();
                    txtTitle.setText(fullName != null ? fullName : "User");
                }
                if (txtDesc != null) {
                    txtDesc.setText(account.getEmail());
                    txtDesc.setAlpha(0.9f);
                }

                // --- LOAD THE GOOGLE PHOTO (FIXED THREADING) ---
                if (imgProfile != null) {
                    android.net.Uri photoUri = account.getPhotoUrl();
                    if (photoUri != null) {
                        imgProfile.setColorFilter(null);

                        // Use a simple Thread to avoid the ExecutorService warning entirely!
                        new Thread(() -> {
                            try {
                                java.io.InputStream in = new java.net.URL(photoUri.toString()).openStream();
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);
                                runOnUiThread(() -> imgProfile.setImageBitmap(bitmap));
                            } catch (Exception e) {
                                android.util.Log.e("DashboardActivity", "Failed to load Google profile photo", e);
                            }
                        }).start();

                    } else {
                        imgProfile.setImageResource(R.drawable.person);
                        imgProfile.setColorFilter(android.graphics.Color.WHITE);
                    }
                }
            } else {
                if (txtTitle != null) txtTitle.setText("Guest User");
                if (txtDesc != null) {
                    txtDesc.setText("Log in to backup trips");
                    txtDesc.setAlpha(0.8f);
                }
                if (imgProfile != null) {
                    imgProfile.setImageResource(R.drawable.person);
                    imgProfile.setColorFilter(android.graphics.Color.WHITE);
                }
            }
        }

        // 2. Update the Bottom Menu Button (Action)
        android.view.Menu menu = navView.getMenu();
        android.view.MenuItem loginToggleItem = menu.findItem(R.id.nav_login_toggle);
        if (loginToggleItem != null) {
            if (account != null) {
                loginToggleItem.setTitle("Log Out");
                loginToggleItem.setIcon(android.R.drawable.ic_lock_power_off);
            } else {
                loginToggleItem.setTitle("Log In with Google");
                loginToggleItem.setIcon(android.R.drawable.ic_menu_myplaces);
            }
        }
    }

    private void signOut() {
        android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes, Log Out", (dialog, which) -> performActualSignOut())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .create();

        // 1. You MUST show the dialog before modifying buttons
        alertDialog.show();

        // 2. Grab the buttons
        android.widget.Button btnPositive = alertDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        android.widget.Button btnNegative = alertDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);

        // 3. Apply your custom Maroon brand color
        btnPositive.setTextColor(android.graphics.Color.parseColor("#85022E"));
        btnNegative.setTextColor(android.graphics.Color.parseColor("#85022E"));

        // 4. Turn off Android's default ALL CAPS styling!
        btnPositive.setAllCaps(false);
        btnNegative.setAllCaps(false);
    }

    private void signInWithGoogle() {
        signInLauncher.launch(mGoogleSignInClient.getSignInIntent());
    }

    private void performActualSignOut() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            clearWorkspace();
            updateSignInUI();
            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            Toast.makeText(DashboardActivity.this, "Successfully Logged Out", Toast.LENGTH_SHORT).show();
        });
    }
}