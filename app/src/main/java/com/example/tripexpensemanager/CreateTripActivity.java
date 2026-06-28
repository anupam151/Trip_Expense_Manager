package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.os.Bundle;
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
import java.util.Locale;

public class CreateTripActivity extends AppCompatActivity {

    private static final String TAG = "CreateTripActivity";
    private EditText edtTripName, edtDestination, edtStartDate, edtEndDate;
    private LinearLayout layoutMemberList;
    private ArrayList<String> memberList;
    private int memberCounter = 1;

    private TripDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_trip);

        dbHelper = new TripDatabaseHelper(this);
        memberList = new ArrayList<>();

        edtTripName = findViewById(R.id.edt_trip_name);
        edtDestination = findViewById(R.id.edt_destination);
        edtStartDate = findViewById(R.id.edt_start_date);
        edtEndDate = findViewById(R.id.edt_end_date);
        layoutMemberList = findViewById(R.id.layout_member_list);

        edtStartDate.setShowSoftInputOnFocus(false);
        edtEndDate.setShowSoftInputOnFocus(false);

        Button btnAddMemberTrigger = findViewById(R.id.btn_add_member_trigger);
        Button btnCreateTripSubmit = findViewById(R.id.btn_create_trip_submit);

        edtStartDate.setOnClickListener(v -> showDatePicker(edtStartDate));
        edtEndDate.setOnClickListener(v -> showDatePicker(edtEndDate));
        btnAddMemberTrigger.setOnClickListener(v -> showAddMemberDialog());
        btnCreateTripSubmit.setOnClickListener(v -> validateAndCreateTrip());
    }

    private void addMemberToLayout(String name) {
        memberList.add(name);

        final LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER_VERTICAL);
        rowLayout.setPadding(0, 8, 0, 8);

        TextView txtMember = new TextView(this);
        String displayedText = String.format(Locale.US, "%d. %s", memberCounter, name);
        txtMember.setText(displayedText);
        txtMember.setTextSize(16);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
        txtMember.setTextColor(0xFF000000);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtMember.setLayoutParams(params);

        TextView btnRemove = createRemoveButtonNode(rowLayout, name);

        rowLayout.addView(txtMember);
        rowLayout.addView(btnRemove);

        layoutMemberList.addView(rowLayout);
        memberCounter++;
    }

    private TextView createRemoveButtonNode(final LinearLayout rowLayout, final String name) {
        TextView btnRemove = new TextView(this);
        btnRemove.setText("✕");
        btnRemove.setTextSize(18);
        btnRemove.setTextColor(0xFFFF3B30);
        btnRemove.setPadding(16, 8, 16, 8);
        btnRemove.setClickable(true);
        btnRemove.setFocusable(true);

        btnRemove.setOnClickListener(v -> {
            layoutMemberList.removeView(rowLayout);
            memberList.remove(name);
            reorderMemberCounter();
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
        if (currentFocusView != null) {
            currentFocusView.clearFocus();
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && currentFocusView != null) {
            imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);
        }

        DatePickerDialog datePickerDialog = createDatePickerDialogInstance(dateEditText);

        // FIXED: Show the dialog first to render the UI tree
        datePickerDialog.show();

        // FIXED: Safely inject the background color into the action button container
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

        // FIXED: Added R.style.CustomCalendarTheme to apply the XML typography constraints
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
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(edtDialogMemberName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100);
        });

        alertDialog.show();

        btnDialogAdd.setOnClickListener(v -> {
            String memberName = edtDialogMemberName.getText().toString().trim();
            if (memberName.isEmpty()) {
                Toast.makeText(this, getString(R.string.err_empty_name), Toast.LENGTH_SHORT).show();
            } else {
                // HIDE KEYBOARD BEFORE DISMISSING
                hideKeyboard(edtDialogMemberName);
                addMemberToLayout(memberName);
                alertDialog.dismiss();
            }
        });

        btnDialogBack.setOnClickListener(v -> {
            // HIDE KEYBOARD BEFORE DISMISSING
            hideKeyboard(edtDialogMemberName);
            alertDialog.dismiss();
        });
    }

    // Add this helper method to your Activity
    private void hideKeyboard(android.view.View view) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void validateAndCreateTrip() {
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
            Log.e(TAG, "Timestamp format mapping failure conversion check", e);
            Toast.makeText(this, getString(R.string.err_invalid_date_format), Toast.LENGTH_SHORT).show();
            return;
        }

        if (memberList.isEmpty()) {
            Toast.makeText(this, getString(R.string.err_min_one_member_required), Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder membersStringBuilder = new StringBuilder();
        for (int i = 0; i < memberList.size(); i++) {
            membersStringBuilder.append(memberList.get(i));
            if (i < memberList.size() - 1) {
                membersStringBuilder.append(",");
            }
        }
        String allMembersStr = membersStringBuilder.toString();
        int totalMembersCount = memberList.size();

        String resultRowId = dbHelper.insertTrip(tripName, destination, allMembersStr, totalMembersCount, startDate, endDate);

        if (resultRowId != null && !resultRowId.equals("-1")) {
            Toast.makeText(this, "Trip created successfully!", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to insert trip record details!", Toast.LENGTH_SHORT).show();
        }
    }
}