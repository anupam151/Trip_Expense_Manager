package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
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
import java.util.Locale;
// Rounded corner dialog
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

// --- Firebase & Auth Imports ---
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CreateTripActivity extends AppCompatActivity {

    private static final String TAG = "CreateTripActivity";
    private EditText edtTripName, edtDestination, edtStartDate, edtEndDate;
    private LinearLayout layoutMemberList;

    // --- CHANGED: Now holds the complex TripMember object instead of just Strings ---
    private ArrayList<TripMember> memberList;

    private int memberCounter = 1;
    private Button btnCreateTripSubmit;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_trip);

        db = FirebaseFirestore.getInstance();
        memberList = new ArrayList<>();

        edtTripName = findViewById(R.id.edt_trip_name);
        edtDestination = findViewById(R.id.edt_destination);
        edtStartDate = findViewById(R.id.edt_start_date);
        edtEndDate = findViewById(R.id.edt_end_date);
        layoutMemberList = findViewById(R.id.layout_member_list);

        edtStartDate.setShowSoftInputOnFocus(false);
        edtEndDate.setShowSoftInputOnFocus(false);

        Button btnAddMemberTrigger = findViewById(R.id.btn_add_member_trigger);
        btnCreateTripSubmit = findViewById(R.id.btn_create_trip_submit);
        android.widget.ImageButton btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> {
            hideKeyboard(v);
            finish();
        });

        edtStartDate.setOnClickListener(v -> showDatePicker(edtStartDate));
        edtEndDate.setOnClickListener(v -> showDatePicker(edtEndDate));
        btnAddMemberTrigger.setOnClickListener(v -> showAddMemberDialog());
        btnCreateTripSubmit.setOnClickListener(v -> validateAndCreateTrip());

        edtTripName.requestFocus();
        edtTripName.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(edtTripName, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    // --- CHANGED: Now accepts a TripMember object ---
    private void addMemberToLayout(TripMember member) {
        memberList.add(member);

        final LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER_VERTICAL);
        rowLayout.setPadding(0, 8, 0, 8);

        TextView txtMember = new TextView(this);
        // Display name on UI
        String displayedText = String.format(Locale.US, "%d. %s", memberCounter, member.getMemberName());
        txtMember.setText(displayedText);
        txtMember.setTextSize(16);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        txtMember.setTextColor(0xFF000000);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtMember.setLayoutParams(params);

        TextView btnRemove = createRemoveButtonNode(rowLayout, member);

        rowLayout.addView(txtMember);
        rowLayout.addView(btnRemove);

        layoutMemberList.addView(rowLayout);
        memberCounter++;
        layoutMemberList.setVisibility(View.VISIBLE);
        findViewById(R.id.txt_no_members).setVisibility(View.GONE);
        findViewById(R.id.txt_members_subtext).setVisibility(View.GONE);
    }

    // --- CHANGED: Now removes the TripMember object ---
    private TextView createRemoveButtonNode(final LinearLayout rowLayout, final TripMember member) {
        TextView btnRemove = new TextView(this);
        btnRemove.setText("✕");
        btnRemove.setTextSize(18);
        btnRemove.setTextColor(0xFFFF3B30);
        btnRemove.setPadding(16, 8, 16, 8);
        btnRemove.setClickable(true);
        btnRemove.setFocusable(true);

        btnRemove.setOnClickListener(v -> {
            layoutMemberList.removeView(rowLayout);
            memberList.remove(member);
            reorderMemberCounter();

            if (memberList.isEmpty()) {
                layoutMemberList.setVisibility(View.GONE);
                findViewById(R.id.txt_no_members).setVisibility(View.VISIBLE);
                findViewById(R.id.txt_members_subtext).setVisibility(View.VISIBLE);
            }
        });
        return btnRemove;
    }

    private void reorderMemberCounter() {
        memberCounter = 1;
        int childCount = layoutMemberList.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = layoutMemberList.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                View textNode = row.getChildAt(0);
                if (textNode instanceof TextView) {
                    TextView txtName = (TextView) textNode;
                    String currentText = txtName.getText().toString();
                    if (currentText.contains(". ")) {
                        String cleanName = currentText.substring(currentText.indexOf(". ") + 2);
                        txtName.setText(String.format(Locale.US, "%d. %s", memberCounter, cleanName));
                    }
                    memberCounter++;
                }
            }
        }
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

        return new DatePickerDialog(this, R.style.CustomCalendarTheme,
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
        // --- NEW: Email Field Reference ---
        final EditText edtDialogMemberEmail = view.findViewById(R.id.edt_dialog_member_email);

        Button btnDialogAdd = view.findViewById(R.id.btn_dialog_add);
        Button btnDialogBack = view.findViewById(R.id.btn_dialog_back);

        txtDialogTitle.setText(getString(R.string.fmt_dialog_member_count_label, memberCounter));
        AlertDialog alertDialog = builder.create();

        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        alertDialog.setOnShowListener(dialogInterface -> {
            edtDialogMemberName.requestFocus();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(edtDialogMemberName, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        });

        alertDialog.show();

        btnDialogAdd.setOnClickListener(v -> {
            String memberName = edtDialogMemberName.getText().toString().trim();
            String memberEmail = edtDialogMemberEmail.getText().toString().trim();

            if (memberName.isEmpty()) {
                Toast.makeText(this, getString(R.string.err_empty_name), Toast.LENGTH_SHORT).show();
            } else if (!memberEmail.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(memberEmail).matches()) {
                // --- NEW: Email Validation ---
                Toast.makeText(this, "Please enter a valid Email ID", Toast.LENGTH_SHORT).show();
            } else {
                // --- UNIQUENESS VALIDATION ---
                boolean nameExists = false;
                boolean emailExists = false;

                for (TripMember m : memberList) {
                    if (m.getMemberName().equalsIgnoreCase(memberName)) nameExists = true;
                    if (!memberEmail.isEmpty() && m.getEmailId() != null && m.getEmailId().equalsIgnoreCase(memberEmail)) emailExists = true;
                }

                if (nameExists) {
                    Toast.makeText(this, "Member '" + memberName + "' is already in the list!", Toast.LENGTH_SHORT).show();
                } else if (emailExists) {
                    Toast.makeText(this, "Email '" + memberEmail + "' is already used by another member!", Toast.LENGTH_SHORT).show();
                } else {
                    hideKeyboard(edtDialogMemberName);

                    // --- CHANGED: Pass the new TripMember Object ---
                    TripMember newMember = new TripMember(memberName, memberEmail.isEmpty() ? null : memberEmail.toLowerCase(), null);
                    addMemberToLayout(newMember);

                    alertDialog.dismiss();
                }
            }
        });

        btnDialogBack.setOnClickListener(v -> {
            hideKeyboard(edtDialogMemberName);
            alertDialog.dismiss();
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }


    private void validateAndCreateTrip() {
        String tripName = edtTripName.getText().toString().trim();
        String destination = edtDestination.getText().toString().trim();
        String startDate = edtStartDate.getText().toString().trim();
        String endDate = edtEndDate.getText().toString().trim();

        // 1. Basic Validation
        if (tripName.isEmpty() || destination.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Format Validation
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        try {
            Date dateStart = sdf.parse(startDate);
            Date dateEnd = sdf.parse(endDate);
            if (dateStart != null && dateEnd != null && dateStart.after(dateEnd)) {
                Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (ParseException e) {
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (memberList.isEmpty()) {
            Toast.makeText(this, "Add at least one member", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Prepare Cloud Data
        StringBuilder membersStringBuilder = new StringBuilder();
        ArrayList<Map<String, Object>> memberDetailsList = new ArrayList<>();
        ArrayList<String> sharedEmailsList = new ArrayList<>();

        for (int i = 0; i < memberList.size(); i++) {
            TripMember mem = memberList.get(i);
            membersStringBuilder.append(mem.getMemberName()).append(i < memberList.size() - 1 ? "," : "");

            Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("memberName", mem.getMemberName());
            memberMap.put("emailId", mem.getEmailId());
            memberMap.put("role", mem.getRole());
            memberDetailsList.add(memberMap);

            if (mem.getEmailId() != null && !mem.getEmailId().isEmpty()) {
                sharedEmailsList.add(mem.getEmailId());
            }
        }

        Map<String, Object> tripData = new HashMap<>();
        tripData.put("tripName", tripName);
        tripData.put("destination", destination);
        tripData.put("startDate", startDate);
        tripData.put("endDate", endDate);
        tripData.put("members", membersStringBuilder.toString());
        tripData.put("inactiveMembers", "");
        tripData.put("memberCount", memberList.size());
        tripData.put("isPinned", false);
        tripData.put("memberDetails", memberDetailsList);
        tripData.put("sharedEmails", sharedEmailsList);

        // 4. GET AUTHENTICATED USER (The Permission Fix)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            tripData.put("ownerEmail", currentUser.getEmail());
            if (!sharedEmailsList.contains(currentUser.getEmail())) {
                sharedEmailsList.add(currentUser.getEmail());
            }
        } else {
            Toast.makeText(this, "You are not logged in!", Toast.LENGTH_LONG).show();
            return;
        }

        // 5. SAVE TO FIRESTORE
        btnCreateTripSubmit.setEnabled(false);
        btnCreateTripSubmit.setText("Creating...");

        db.collection("Trips").add(tripData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Trip created successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity only on success
                })
                .addOnFailureListener(e -> {
                    btnCreateTripSubmit.setEnabled(true);
                    btnCreateTripSubmit.setText("Create Trip");
                    Log.e(TAG, "Error adding document", e);
                    Toast.makeText(this, "Permission Denied or Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}