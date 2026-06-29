package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {

    private static final String TAG = "AddExpenseActivity";

    private EditText edtPurpose, edtAmount;
    private EditText etExpenseDate;
    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private Spinner spinnerPaidBy;
    private GridLayout layoutCheckboxContainer;

    private TripDatabaseHelper dbHelper;
    private String currentTripId;
    private final ArrayList<String> parsedMembersList = new ArrayList<>();
    private final ArrayList<CheckBox> activeCheckBoxesReferences = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        edtPurpose = findViewById(R.id.edt_expense_purpose);
        edtAmount = findViewById(R.id.edt_expense_amount);
        etExpenseDate = findViewById(R.id.et_expense_date);
        spinnerPaidBy = findViewById(R.id.spinner_paid_by);
        layoutCheckboxContainer = findViewById(R.id.layout_checkbox_container);
        Button btnSaveExpense = findViewById(R.id.btn_save_expense);

        // --- NEW: Request focus on the first input box ---
        edtPurpose.requestFocus();

        // --- NEW: Force the keyboard to open after a slight delay ---
        edtPurpose.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(edtPurpose, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);

        etExpenseDate.setShowSoftInputOnFocus(false);

        dbHelper = new TripDatabaseHelper(this);
        etExpenseDate.setText(dateFormatter.format(calendar.getTime()));
        etExpenseDate.setOnClickListener(v -> showDatePicker());

        extractIncomingIntentData();
        btnSaveExpense.setOnClickListener(v -> executeExpenseValidationPipeline());
    }

    private void showDatePicker() {
        View currentFocusView = getCurrentFocus();
        if (currentFocusView != null) {
            currentFocusView.clearFocus();
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && currentFocusView != null) {
            imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);
        }

        DatePickerDialog datePickerDialog = createDatePickerDialogInstance();
        datePickerDialog.show();

        // FIXED: Grab the bottom bar and paint it solid maroon
        Button positiveButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            View buttonPanel = (View) positiveButton.getParent();
            // Forces maroon (#85022E) unconditionally for both light and night modes
            buttonPanel.setBackgroundColor(android.graphics.Color.parseColor("#85022E"));
        }
    }

    @NonNull
    private DatePickerDialog createDatePickerDialogInstance() {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        return new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    calendar.set(Calendar.YEAR, selectedYear);
                    calendar.set(Calendar.MONTH, selectedMonth);
                    calendar.set(Calendar.DAY_OF_MONTH, selectedDay);
                    etExpenseDate.setText(dateFormatter.format(calendar.getTime()));
                },
                year, month, day
        );
    }

    private void extractIncomingIntentData() {
        if (getIntent() != null) {
            currentTripId = getIntent().getStringExtra("TRIP_ID");
            String rawMembersStr = getIntent().getStringExtra("TRIP_MEMBERS");

            Log.d(TAG, "Initializing transaction context for trip signature: " + currentTripId);

            if (rawMembersStr != null && !rawMembersStr.trim().isEmpty()) {
                String[] splitNames = rawMembersStr.split(",");
                for (String name : splitNames) {
                    if (!name.trim().isEmpty()) {
                        parsedMembersList.add(name.trim());
                    }
                }

                Collections.sort(parsedMembersList);
                populatePaidBySpinner();
                generateDynamicMembersCheckboxes();
            } else {
                Toast.makeText(this, "Error: No trip members context available!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // --- UPDATED SECTION START ---

    private void populatePaidBySpinner() {
        ArrayList<String> spinnerOptions = new ArrayList<>();

        // Add "Fund" as the first option
        spinnerOptions.add("Fund");

        // Add all trip members after "Fund"
        spinnerOptions.addAll(parsedMembersList);

        // Call the extracted method to generate the adapter
        ArrayAdapter<String> adapter = createSpinnerAdapter(spinnerOptions);

        spinnerPaidBy.setAdapter(adapter);

        // Set "Fund" (index 0) as the default selected option
        spinnerPaidBy.setSelection(0);
    }

    @NonNull
    private ArrayAdapter<String> createSpinnerAdapter(ArrayList<String> options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                return super.getView(position, convertView, parent);
            }

            @NonNull
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                return super.getDropDownView(position, convertView, parent);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    // --- UPDATED SECTION END ---

    private void generateDynamicMembersCheckboxes() {
        layoutCheckboxContainer.removeAllViews();
        activeCheckBoxesReferences.clear();

        float density = getResources().getDisplayMetrics().density;
        int padding8dpInPx = Math.round(8.0f * density);

        for (String memberName : parsedMembersList) {
            CheckBox checkBox = createMemberCheckBox(memberName, padding8dpInPx);
            activeCheckBoxesReferences.add(checkBox);
            layoutCheckboxContainer.addView(checkBox);
        }
    }

    private CheckBox createMemberCheckBox(String memberName, int paddingPx) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(memberName);
        checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

        // ALWAYS Gray Checkbox
        checkBox.setButtonTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.checkbox_state_colors));

        // ALWAYS Black Text (Removed the dark mode check)
        checkBox.setTextColor(android.graphics.Color.parseColor("#000000"));

        checkBox.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        checkBox.setChecked(true);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        checkBox.setLayoutParams(params);

        return checkBox;
    }

    private void executeExpenseValidationPipeline() {
        String purpose = edtPurpose.getText().toString().trim();
        String amountRaw = edtAmount.getText().toString().trim();
        String expenseDateStr = etExpenseDate.getText().toString().trim();

        if (!purpose.matches(".*[a-zA-Z].*")) {
            Toast.makeText(this, "Expense description must contain at least one alphabet character!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amountRaw.isEmpty()) {
            Toast.makeText(this, "Please enter a valid numeric split amount!", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalAmount;
        try {
            totalAmount = Double.parseDouble(amountRaw);
            if (totalAmount <= 0) {
                Toast.makeText(this, "Expense amount must be greater than zero!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid currency numbers format schema!", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> participatingMembersList = new ArrayList<>();
        for (CheckBox cb : activeCheckBoxesReferences) {
            if (cb.isChecked()) {
                participatingMembersList.add(cb.getText().toString().trim());
            }
        }

        if (participatingMembersList.isEmpty()) {
            Toast.makeText(this, "At least one member must be selected to split this expense!", Toast.LENGTH_LONG).show();
            return;
        }

        // ... (existing code above these stays the same) ...

        String selectedPayer = spinnerPaidBy.getSelectedItem().toString();
        int totalSelectedConsumersCount = participatingMembersList.size();
        double equalSplitDebitShareAmount = totalAmount / totalSelectedConsumersCount;

        // --- NEW FUND BALANCE CHECK START ---
        if (selectedPayer.equals("Fund")) {
            // Ask the database for the real-time calculated fund balance
            double currentFundBalance = dbHelper.getFundBalance(currentTripId);

            // If the expense is bigger than the fund, block the save and show an error!
            if (totalAmount > currentFundBalance) {
                String errorMsg = String.format(Locale.US,
                        "Insufficient Fund Balance! Available: ₹%.2f", currentFundBalance);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return; // Stops the code here so the expense is NOT saved
            }
        }
        // --- NEW FUND BALANCE CHECK END ---

        String joinedSharedWithText = TextUtils.join(", ", participatingMembersList);
        long insertedRowId = dbHelper.insertExpense(currentTripId, purpose, totalAmount, selectedPayer, joinedSharedWithText, expenseDateStr);

        // ... (existing code below these stays the same) ...

        if (insertedRowId != -1) {
            String feedbackMessage = String.format(Locale.US,
                    "Saved! Share per person: ₹%.2f among %d members.",
                    equalSplitDebitShareAmount, totalSelectedConsumersCount);

            Toast.makeText(this, feedbackMessage, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Database failure: Unable to register transaction details!", Toast.LENGTH_LONG).show();
        }
    }
}