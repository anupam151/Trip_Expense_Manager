package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.os.Bundle;
//import android.text.TextUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
// Rounded corner dialog
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
// Rounded corner dialog End

// --- Firebase ---
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.firestore.FirebaseFirestore;

@SuppressWarnings("deprecation")
public class UpdateTripActivity extends AppCompatActivity {

    private static final String TAG = "UpdateTripActivity";
    private EditText edtTripName, edtDestination, edtStartDate, edtEndDate;
    private LinearLayout layoutMemberList;

    // --- CHANGED: Using Complex TripMember Objects ---
    private ArrayList<TripMember> memberList;
    private ArrayList<TripMember> inactiveMembers;

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
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
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

        // --- CHANGED: Pass null because we are adding a NEW member ---
        btnAddMemberTrigger.setOnClickListener(v -> showAddOrEditMemberDialog(null));
        btnUpdateTripSubmit.setOnClickListener(v -> validateAndUpdateTrip());

        extractAndPrefillData();
    }

    @SuppressWarnings("unchecked")
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
                        memberList.clear();
                        inactiveMembers.clear();

                        // 1. Try to load the Rich 'memberDetails' Array
                        List<Map<String, Object>> rawMemberDetails = (List<Map<String, Object>>) doc.get("memberDetails");

                        // 2. Fetch the Legacy Strings
                        String rawActive = doc.getString("members");
                        String rawInactive = doc.getString("inactiveMembers");

                        if (rawMemberDetails != null && !rawMemberDetails.isEmpty()) {
                            // Extract rich data for active members
                            for (Map<String, Object> map : rawMemberDetails) {
                                String mName = (String) map.get("memberName");
                                String mEmail = (String) map.get("emailId");
                                String mRole = (String) map.get("role");
                                memberList.add(new TripMember(mName, mEmail, mRole));
                            }
                        } else if (rawActive != null && !rawActive.trim().isEmpty()) {
                            // Legacy fallback if memberDetails doesn't exist yet
                            for (String name : rawActive.split(",")) {
                                if (!name.trim().isEmpty()) memberList.add(new TripMember(name.trim(), null, null));
                            }
                        }

                        // Load Inactive members
                        if (rawInactive != null && !rawInactive.trim().isEmpty()) {
                            for (String name : rawInactive.split(",")) {
                                if (!name.trim().isEmpty()) inactiveMembers.add(new TripMember(name.trim(), null, null));
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

        for (TripMember member : memberList) {
            addMemberRow(member, true);
        }

        if (!inactiveMembers.isEmpty()) {
            TextView header = new TextView(this);
            header.setText("Inactive Members (Soft Deleted)");
            header.setTextSize(14);
            header.setTextColor(Color.GRAY);
            header.setPadding(0, 32, 0, 16);
            layoutMemberList.addView(header);

            for (TripMember member : inactiveMembers) {
                addMemberRow(member, false);
            }
        }

        if (memberList.isEmpty()) {
            findViewById(R.id.txt_update_no_members).setVisibility(View.VISIBLE);
            findViewById(R.id.txt_update_members_subtext).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.txt_update_no_members).setVisibility(View.GONE);
            findViewById(R.id.txt_update_members_subtext).setVisibility(View.GONE);
        }
    }

    private void addMemberRow(final TripMember member, boolean isActive) {
        final LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER_VERTICAL);
        rowLayout.setPadding(0, 8, 0, 8);

        TextView txtMember = new TextView(this);

        // --- NEW: Added the ✎ pencil icon so the Admin knows they can edit the email! ---
        String nameText = member.getMemberName();
        String displayedText = isActive ? String.format(Locale.US, "%d. %s  ✎", memberCounter, nameText) : "• " + nameText;

        txtMember.setText(displayedText);
        txtMember.setTextSize(16);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        txtMember.setTextColor(isActive ? 0xFF000000 : Color.GRAY);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtMember.setLayoutParams(params);

        // --- NEW: Make the text clickable to edit the member ---
        if (isActive) {
            txtMember.setOnClickListener(v -> showAddOrEditMemberDialog(member));
        }

        TextView btnAction = createActionButton(member, isActive);

        rowLayout.addView(txtMember);
        rowLayout.addView(btnAction);

        layoutMemberList.addView(rowLayout);
        if (isActive) memberCounter++;
    }

    private TextView createActionButton(final TripMember member, boolean isActive) {
        TextView btnAction = new TextView(this);
        btnAction.setText(isActive ? "✕" : "↺");
        btnAction.setTextSize(18);
        btnAction.setTextColor(isActive ? 0xFFFF3B30 : 0xFF2E7D32);
        btnAction.setPadding(16, 8, 16, 8);
        btnAction.setClickable(true);
        btnAction.setFocusable(true);

        btnAction.setOnClickListener(v -> {
            if (isActive) {
                memberList.remove(member);
                if (!inactiveMembers.contains(member)) inactiveMembers.add(member);
            } else {
                inactiveMembers.remove(member);
                if (!memberList.contains(member)) memberList.add(member);
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
            buttonPanel.setBackgroundColor(Color.parseColor("#85022E"));
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

    // --- NEW: Handles both Adding a New Member AND Editing an Existing Member ---
    private void showAddOrEditMemberDialog(TripMember memberToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_member, null);
        builder.setView(view);

        TextView txtDialogTitle = view.findViewById(R.id.txt_dialog_title);
        final EditText edtDialogMemberName = view.findViewById(R.id.edt_dialog_member_name);
        final EditText edtDialogMemberEmail = view.findViewById(R.id.edt_dialog_member_email);

        Button btnDialogAdd = view.findViewById(R.id.btn_dialog_add);
        Button btnDialogBack = view.findViewById(R.id.btn_dialog_back);

        // Prefill Data if we are editing!
        if (memberToEdit == null) {
            txtDialogTitle.setText(getString(R.string.fmt_dialog_member_count_label, memberCounter));
            btnDialogAdd.setText(getString(R.string.add));
        } else {
            txtDialogTitle.setText("Edit Member");
            btnDialogAdd.setText("Save");
            edtDialogMemberName.setText(memberToEdit.getMemberName());
            if (memberToEdit.getEmailId() != null) {
                edtDialogMemberEmail.setText(memberToEdit.getEmailId());
            }
        }

        AlertDialog alertDialog = builder.create();
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnDialogAdd.setOnClickListener(v -> {
            String newName = edtDialogMemberName.getText().toString().trim();
            String newEmail = edtDialogMemberEmail.getText().toString().trim();

            if (newName.isEmpty()) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newEmail.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(this, "Please enter a valid Email ID", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- GLOBAL UNIQUENESS CHECK (Skipping the current member being edited) ---
            boolean isDuplicate = false;

            for (TripMember m : memberList) {
                if (m != memberToEdit && m.getMemberName().equalsIgnoreCase(newName)) { isDuplicate = true; break; }
                if (m != memberToEdit && !newEmail.isEmpty() && m.getEmailId() != null && m.getEmailId().equalsIgnoreCase(newEmail)) {
                    Toast.makeText(this, "Email is already used by another member!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            if (!isDuplicate) {
                for (TripMember m : inactiveMembers) {
                    if (m != memberToEdit && m.getMemberName().equalsIgnoreCase(newName)) { isDuplicate = true; break; }
                }
            }

            if (isDuplicate) {
                Toast.makeText(this, "Member '" + newName + "' already exists (Active or Inactive)!", Toast.LENGTH_SHORT).show();
            } else {
                hideKeyboard(edtDialogMemberName);

                // --- FIX: Calculate the final email ONCE to satisfy Android Studio ---
                String finalEmail = newEmail.isEmpty() ? null : newEmail.toLowerCase();

                if (memberToEdit == null) {
                    // Create New
                    memberList.add(new TripMember(newName, finalEmail, null));
                } else {
                    // Update Existing
                    memberToEdit.setMemberName(newName);
                    memberToEdit.setEmailId(finalEmail);
                }

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

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void validateAndUpdateTrip() {
        String tripName = edtTripName.getText().toString().trim();
        String destination = edtDestination.getText().toString().trim();
        String startDate = edtStartDate.getText().toString().trim();
        String endDate = edtEndDate.getText().toString().trim();

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

        // --- NEW: Generate our structured Cloud Data ---
        StringBuilder membersBuilder = new StringBuilder();
        ArrayList<Map<String, Object>> memberDetailsList = new ArrayList<>();
        ArrayList<String> sharedEmailsList = new ArrayList<>();

        for (int i = 0; i < memberList.size(); i++) {
            TripMember mem = memberList.get(i);

            // 1. Build legacy string
            membersBuilder.append(mem.getMemberName());
            if (i < memberList.size() - 1) membersBuilder.append(",");

            // 2. Build Complex Member Details
            Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("memberName", mem.getMemberName());
            memberMap.put("emailId", mem.getEmailId());
            memberMap.put("role", mem.getRole()); // Preserve existing role!
            memberDetailsList.add(memberMap);

            // 3. Build Search Index Array
            if (mem.getEmailId() != null && !mem.getEmailId().isEmpty()) {
                String role = mem.getRole();
                boolean isNoAccess = role != null && role.trim().equalsIgnoreCase("No Access");

                if (!isNoAccess) {
                    sharedEmailsList.add(mem.getEmailId()); // Only add if they are allowed access!
                }
            }
        }

        // Handle Legacy Inactive String
        StringBuilder inactiveBuilder = new StringBuilder();
        for (int i = 0; i < inactiveMembers.size(); i++) {
            inactiveBuilder.append(inactiveMembers.get(i).getMemberName());
            if (i < inactiveMembers.size() - 1) inactiveBuilder.append(",");
        }

        // Ensure the Creator is always in the Search Index
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null && !sharedEmailsList.contains(account.getEmail())) {
            sharedEmailsList.add(account.getEmail());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("tripName", tripName);
        updates.put("destination", destination);
        updates.put("startDate", startDate);
        updates.put("endDate", endDate);
        updates.put("members", membersBuilder.toString());
        updates.put("inactiveMembers", inactiveBuilder.toString());
        updates.put("memberCount", memberList.size());

        // Push the Rich Data
        updates.put("memberDetails", memberDetailsList);
        updates.put("sharedEmails", sharedEmailsList);

        btnUpdateTripSubmit.setEnabled(false);
        btnUpdateTripSubmit.setText("Updating...");

        db.collection("Trips").document(tripId)
                .update(updates)
                .addOnFailureListener(e -> {
                    btnUpdateTripSubmit.setEnabled(true);
                    btnUpdateTripSubmit.setText("Update Trip");
                    Toast.makeText(this, getString(R.string.err_trip_update_failed) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

        Toast.makeText(this, "Trip Updated!", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}