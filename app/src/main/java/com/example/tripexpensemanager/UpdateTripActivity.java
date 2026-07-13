package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
// Rounded corner dialog
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
// Rounded corner dialog End

// --- Firebase ---
import com.google.firebase.firestore.FirebaseFirestore;

public class UpdateTripActivity extends AppCompatActivity {

    private static final String TAG = "UpdateTripActivity";
    private EditText edtTripName, edtDestination, edtStartDate, edtEndDate;
    private LinearLayout layoutMemberList;

    private ArrayList<String> memberList;
    private ArrayList<String> inactiveMembers;

    private int memberCounter = 1;
    private String tripId;
    private Button btnUpdateTripSubmit;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_trip);

        android.widget.ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            android.view.View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
            }
            finish();
        });

        db = FirebaseFirestore.getInstance();
        memberList = new ArrayList<>();
        inactiveMembers = new ArrayList<>();

        edtTripName = findViewById(R.id.edt_update_trip_name);
        edtDestination = findViewById(R.id.edt_update_destination);
        edtStartDate = findViewById(R.id.edt_update_start_date);
        edtEndDate = findViewById(R.id.edt_update_end_date);
        layoutMemberList = findViewById(R.id.layout_update_member_list);

        edtStartDate.setShowSoftInputOnFocus(false);
        edtEndDate.setShowSoftInputOnFocus(false);

        Button btnAddMemberTrigger = findViewById(R.id.btn_update_member_trigger);
        btnUpdateTripSubmit = findViewById(R.id.btn_update_trip_submit);

        edtStartDate.setOnClickListener(v -> showDatePicker(edtStartDate));
        edtEndDate.setOnClickListener(v -> showDatePicker(edtEndDate));

        btnAddMemberTrigger.setOnClickListener(v -> showAddMemberDialog());
        btnUpdateTripSubmit.setOnClickListener(v -> validateAndUpdateTrip());

        extractAndPrefillData();
    }
    private void extractAndPrefillData() {
        if (getIntent() != null) {
            tripId = getIntent().getStringExtra("TRIP_ID");
            String tripName = getIntent().getStringExtra("TRIP_NAME");
            String destination = getIntent().getStringExtra("TRIP_DESTINATION");
            String startDate = getIntent().getStringExtra("TRIP_START_DATE");
            String endDate = getIntent().getStringExtra("TRIP_END_DATE");

            edtTripName.setText(tripName);
            edtDestination.setText(destination);
            edtStartDate.setText(startDate);
            edtEndDate.setText(endDate);

            if (tripId != null) {
                db.collection("Trips").document(tripId).get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String rawActive = doc.getString("members");
                        String rawInactive = doc.getString("inactiveMembers");

                        memberList.clear();
                        inactiveMembers.clear();

                        if (rawActive != null && !rawActive.trim().isEmpty()) {
                            for (String name : rawActive.split(",")) {
                                if (!name.trim().isEmpty()) memberList.add(name.trim());
                            }
                        }

                        if (rawInactive != null && !rawInactive.trim().isEmpty()) {
                            for (String name : rawInactive.split(",")) {
                                if (!name.trim().isEmpty()) inactiveMembers.add(name.trim());
                            }
                        }

                        refreshMembersUI();
                    }
                }).addOnFailureListener(e -> Log.e(TAG, "Failed to load members", e));
            }
        }
    }
    private void refreshMembersUI() {
        layoutMemberList.removeAllViews();
        memberCounter = 1;

        for (String name : memberList) {
            addMemberRow(name, true);
        }

        if (!inactiveMembers.isEmpty()) {
            TextView header = new TextView(this);
            header.setText("Inactive Members (Soft Deleted)");
            header.setTextSize(14);
            header.setTextColor(Color.GRAY);
            header.setPadding(0, 32, 0, 16);
            layoutMemberList.addView(header);

            for (String name : inactiveMembers) {
                addMemberRow(name, false);
            }
        }

        if (memberList.isEmpty()) {
            findViewById(R.id.txt_update_no_members).setVisibility(android.view.View.VISIBLE);
            findViewById(R.id.txt_update_members_subtext).setVisibility(android.view.View.VISIBLE);
        } else {
            findViewById(R.id.txt_update_no_members).setVisibility(android.view.View.GONE);
            findViewById(R.id.txt_update_members_subtext).setVisibility(android.view.View.GONE);
        }
    }
    private void addMemberRow(final String name, boolean isActive) {
        final LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER_VERTICAL);
        rowLayout.setPadding(0, 8, 0, 8);

        TextView txtMember = new TextView(this);
        String displayedText = isActive ? String.format(Locale.US, "%d. %s", memberCounter, name) : "• " + name;
        txtMember.setText(displayedText);
        txtMember.setTextSize(16);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        txtMember.setTextColor(isActive ? 0xFF000000 : Color.GRAY);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtMember.setLayoutParams(params);

        // --- WARNING FIX: Extracted the button logic to a separate method! ---
        TextView btnAction = createActionButton(name, isActive);

        rowLayout.addView(txtMember);
        rowLayout.addView(btnAction);

        layoutMemberList.addView(rowLayout);
        if (isActive) memberCounter++;
    }
    // --- NEW: Helper method to keep addMemberRow clean ---
    private TextView createActionButton(final String name, boolean isActive) {
        TextView btnAction = new TextView(this);
        btnAction.setText(isActive ? "✕" : "↺");
        btnAction.setTextSize(18);
        btnAction.setTextColor(isActive ? 0xFFFF3B30 : 0xFF2E7D32);
        btnAction.setPadding(16, 8, 16, 8);
        btnAction.setClickable(true);
        btnAction.setFocusable(true);

        btnAction.setOnClickListener(v -> {
            if (isActive) {
                memberList.remove(name);
                if (!inactiveMembers.contains(name)) inactiveMembers.add(name);
            } else {
                inactiveMembers.remove(name);
                if (!memberList.contains(name)) memberList.add(name);
            }
            refreshMembersUI();
        });

        return btnAction;
    }
    private void showDatePicker(EditText dateEditText) {
        View currentFocusView = getCurrentFocus();
        if (currentFocusView != null) currentFocusView.clearFocus();

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && currentFocusView != null) {
            imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);
        }

        DatePickerDialog datePickerDialog = createDatePickerDialogInstance(dateEditText);
        datePickerDialog.show();

        Window window = datePickerDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setBackgroundDrawableResource(R.drawable.bg_date_picker_dialog);
        }

        Button positiveButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            View buttonPanel = (View) positiveButton.getParent();
            buttonPanel.setBackgroundColor(android.graphics.Color.parseColor("#85022E"));
        }
    }
    @NonNull
    private DatePickerDialog createDatePickerDialogInstance(EditText dateEditText) {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        return new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String formattedDate = String.format(Locale.US, "%02d/%02d/%04d", selectedDay, (selectedMonth + 1), selectedYear);
                    dateEditText.setText(formattedDate);
                }, year, month, day);
    }
    private void showAddMemberDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_member, null);
        builder.setView(view);

        TextView txtDialogTitle = view.findViewById(R.id.txt_dialog_title);
        final EditText edtDialogMemberName = view.findViewById(R.id.edt_dialog_member_name);
        Button btnDialogAdd = view.findViewById(R.id.btn_dialog_add); // Ensure this ID matches your XML
        Button btnDialogBack = view.findViewById(R.id.btn_dialog_back);

        txtDialogTitle.setText(getString(R.string.fmt_dialog_member_count_label, memberCounter));
        AlertDialog alertDialog = builder.create();

        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnDialogAdd.setOnClickListener(v -> {
            String newName = edtDialogMemberName.getText().toString().trim();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- GLOBAL UNIQUENESS CHECK ---
            boolean isDuplicate = false;
            for (String name : memberList) {
                if (name.equalsIgnoreCase(newName)) { isDuplicate = true; break; }
            }
            if (!isDuplicate) {
                for (String name : inactiveMembers) {
                    if (name.equalsIgnoreCase(newName)) { isDuplicate = true; break; }
                }
            }

            if (isDuplicate) {
                Toast.makeText(this, "Member '" + newName + "' already exists (Active or Inactive)!", Toast.LENGTH_SHORT).show();
            } else {
                hideKeyboard(edtDialogMemberName);

                // Add to active list
                memberList.add(newName);
                refreshMembersUI();
                alertDialog.dismiss();
            }
        });

        btnDialogBack.setOnClickListener(v -> {
            hideKeyboard(edtDialogMemberName);
            alertDialog.dismiss();
        });

        alertDialog.show();
    }
    private void hideKeyboard(android.view.View view) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    private void validateAndUpdateTrip() {
        String tripName = edtTripName.getText().toString().trim();
        String destination = edtDestination.getText().toString().trim();
        String startDate = edtStartDate.getText().toString().trim();
        String endDate = edtEndDate.getText().toString().trim();

        // --- Validation Checks ---
        if (tripName.isEmpty() || destination.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(this, getString(R.string.err_mandatory_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!tripName.matches(".*[a-zA-Z].*")) {
            Toast.makeText(this, getString(R.string.err_trip_name_alphabet_required), Toast.LENGTH_LONG).show();
            return;
        }

        if (!destination.matches(".*[a-zA-Z].*")) {
            Toast.makeText(this, getString(R.string.err_destination_alphabet_required), Toast.LENGTH_LONG).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        try {
            Date dateStart = sdf.parse(startDate);
            Date dateEnd = sdf.parse(endDate);
            if (dateStart != null && dateEnd != null && dateStart.after(dateEnd)) {
                Toast.makeText(this, getString(R.string.err_date_chronology_mismatch), Toast.LENGTH_LONG).show();
                return;
            }
        } catch (ParseException e) {
            Log.e(TAG, "Timestamp calculation error", e);
            Toast.makeText(this, getString(R.string.err_invalid_date_format), Toast.LENGTH_SHORT).show();
            return;
        }

        if (memberList.isEmpty()) {
            Toast.makeText(this, getString(R.string.err_min_one_member_required), Toast.LENGTH_SHORT).show();
            return;
        }

        // --- Prepare Updates ---
        String allMembersStr = TextUtils.join(",", memberList);
        String allInactiveMembersStr = TextUtils.join(",", inactiveMembers);
        int totalMembersCount = memberList.size();

        Map<String, Object> updates = new HashMap<>();
        updates.put("tripName", tripName);
        updates.put("destination", destination);
        updates.put("startDate", startDate);
        updates.put("endDate", endDate);
        updates.put("members", allMembersStr);
        updates.put("inactiveMembers", allInactiveMembersStr);
        updates.put("memberCount", totalMembersCount);

        // --- Perform Update (Offline-First) ---
        btnUpdateTripSubmit.setEnabled(false);
        btnUpdateTripSubmit.setText("Updating...");

        db.collection("Trips").document(tripId)
                .update(updates)
                .addOnFailureListener(e -> {
                    // If it fails (e.g., permissions), re-enable the button
                    btnUpdateTripSubmit.setEnabled(true);
                    btnUpdateTripSubmit.setText("Update Trip");
                    Toast.makeText(this, getString(R.string.err_trip_update_failed) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

        // --- Close Immediately ---
        // This executes whether online or offline
        Toast.makeText(this, "Trip Updated!", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}