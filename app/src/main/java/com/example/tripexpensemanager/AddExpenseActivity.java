package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {

    private EditText edtPurpose, edtAmount;
    private EditText etExpenseDate;
    private Spinner spinnerPaidBy;
    private GridLayout layoutCheckboxContainer;

    // UI Elements for Live Summary
    private TextView txtSelectedCountPreview, txtSplitAmountPreview;

    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private TripDatabaseHelper dbHelper;
    private String currentTripId;
    private final ArrayList<String> parsedMembersList = new ArrayList<>();
    private final ArrayList<CheckBox> activeCheckBoxesReferences = new ArrayList<>();

    private boolean isEditMode = false;
    private int editTransactionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        // Map UI elements
        edtPurpose = findViewById(R.id.edt_expense_purpose);
        edtAmount = findViewById(R.id.edt_expense_amount);
        etExpenseDate = findViewById(R.id.et_expense_date);
        spinnerPaidBy = findViewById(R.id.spinner_paid_by);
        layoutCheckboxContainer = findViewById(R.id.layout_checkbox_container);
        TextView txtHeading = findViewById(R.id.txt_expense_heading);
        Button btnSaveExpense = findViewById(R.id.btn_save_expense);

        // Map new Live Summary elements
        txtSelectedCountPreview = findViewById(R.id.txt_selected_count_preview);
        txtSplitAmountPreview = findViewById(R.id.txt_split_amount_preview);

        // Setup Custom Back Button
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                View currentFocus = getCurrentFocus();
                if (imm != null && currentFocus != null) {
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
                finish();
            });
        }

        // Live Math Logic
        edtAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculateAndDisplaySplit();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Focus and Keyboard logic
        edtPurpose.requestFocus();
        edtPurpose.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edtPurpose, InputMethodManager.SHOW_IMPLICIT);
        }, 200);

        etExpenseDate.setShowSoftInputOnFocus(false);
        dbHelper = new TripDatabaseHelper(this);
        etExpenseDate.setText(dateFormatter.format(calendar.getTime()));
        etExpenseDate.setOnClickListener(v -> showDatePicker());

        extractIncomingIntentData();

        if (isEditMode && editTransactionId != -1) {
            txtHeading.setText(R.string.edit_expense);
            btnSaveExpense.setText(R.string.update_expense);
            loadExistingExpenseData();
        } else {
            txtHeading.setText(R.string.add_new_expense);
        }

        btnSaveExpense.setOnClickListener(v -> executeExpenseValidationPipeline());

        // Run initial calculation to update default text
        calculateAndDisplaySplit();
    }

    // --- NEW: Live Calculation Method ---
    private void calculateAndDisplaySplit() {
        String amountString = edtAmount.getText().toString().trim();
        double totalAmount = 0.0;

        if (!amountString.isEmpty()) {
            try {
                totalAmount = Double.parseDouble(amountString);
            } catch (NumberFormatException e) {
                totalAmount = 0.0;
            }
        }

        int selectedCount = 0;
        for (CheckBox cb : activeCheckBoxesReferences) {
            if (cb.isChecked()) {
                selectedCount++;
            }
        }

        double splitAmount = 0.0;
        if (selectedCount > 0) {
            splitAmount = totalAmount / selectedCount;
        }

        txtSelectedCountPreview.setText(getString(R.string.format_selected_members, selectedCount));
        txtSplitAmountPreview.setText(String.format(Locale.getDefault(), "₹ %.2f", splitAmount));
    }

    private void extractIncomingIntentData() {
        if (getIntent() != null) {
            currentTripId = getIntent().getStringExtra("TRIP_ID");
            isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
            editTransactionId = getIntent().getIntExtra("TRANS_ID", -1);

            List<String> allMembers = new ArrayList<>();
            String rawMembersStr = getIntent().getStringExtra("TRIP_MEMBERS");
            if (rawMembersStr != null && !rawMembersStr.trim().isEmpty()) {
                for (String name : rawMembersStr.split(",")) {
                    if (!name.trim().isEmpty() && !allMembers.contains(name.trim())) {
                        allMembers.add(name.trim());
                    }
                }
            }

            if (isEditMode) {
                for (String historical : getHistoricalMembers()) {
                    if (!allMembers.contains(historical)) {
                        allMembers.add(historical);
                    }
                }
            }

            parsedMembersList.clear();
            parsedMembersList.addAll(allMembers);

            if (parsedMembersList.isEmpty()) {
                Toast.makeText(this, "No members found!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Collections.sort(parsedMembersList);
                populatePaidBySpinner();
                generateDynamicMembersCheckboxes();
            }
        }
    }

    private ArrayList<String> getHistoricalMembers() {
        ArrayList<String> allMembers = new ArrayList<>();
        String query1 = "SELECT DISTINCT expense_paid_by FROM expenses WHERE expense_trip_id = ?";
        String query2 = "SELECT DISTINCT expense_shared_with FROM expenses WHERE expense_trip_id = ?";
        String query3 = "SELECT DISTINCT payment_by FROM payments WHERE payment_trip_id = ?";

        addNamesToAllMembers(allMembers, query1);
        addNamesToAllMembers(allMembers, query2);
        addNamesToAllMembers(allMembers, query3);

        return allMembers;
    }

    private void addNamesToAllMembers(ArrayList<String> allMembers, String query) {
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(query, new String[]{currentTripId})) {
            while (c.moveToNext()) {
                String raw = c.getString(0);
                if (raw != null && !raw.isEmpty()) {
                    for (String name : raw.split(",")) {
                        String cleanName = name.trim();
                        if (!cleanName.isEmpty() && !"Fund".equalsIgnoreCase(cleanName) && !allMembers.contains(cleanName)) {
                            allMembers.add(cleanName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AddExpenseActivity", "Error fetching historical members: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadExistingExpenseData() {
        try (Cursor c = dbHelper.getExpenseById(editTransactionId)) {
            if (c.moveToFirst()) {
                edtPurpose.setText(c.getString(c.getColumnIndexOrThrow("expense_purpose")));
                edtAmount.setText(String.valueOf(c.getDouble(c.getColumnIndexOrThrow("expense_amount"))));
                etExpenseDate.setText(c.getString(c.getColumnIndexOrThrow("expense_date")));

                String paidBy = c.getString(c.getColumnIndexOrThrow("expense_paid_by"));
                if (spinnerPaidBy.getAdapter() instanceof ArrayAdapter) {
                    ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerPaidBy.getAdapter();
                    for (int i = 0; i < adapter.getCount(); i++) {
                        if (paidBy.equals(adapter.getItem(i))) {
                            spinnerPaidBy.setSelection(i);
                            break;
                        }
                    }
                }

                String sharedWith = c.getString(c.getColumnIndexOrThrow("expense_shared_with"));
                if (sharedWith != null) {
                    List<String> sharedList = Arrays.asList(sharedWith.split(",\\s*"));
                    for (CheckBox cb : activeCheckBoxesReferences) {
                        cb.setChecked(sharedList.contains(cb.getText().toString().trim()));
                    }
                }

                // Recalculate after loading edit data
                calculateAndDisplaySplit();
            }
        }
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            calendar.set(selectedYear, selectedMonth, selectedDay);
            etExpenseDate.setText(dateFormatter.format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.show();
        Window window = datePickerDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setBackgroundDrawableResource(R.drawable.bg_date_picker_dialog);
        }

        Button positiveButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            ((View) positiveButton.getParent()).setBackgroundColor(android.graphics.Color.parseColor("#85022E"));
        }
    }

    private void populatePaidBySpinner() {
        ArrayList<String> spinnerOptions = new ArrayList<>();
        spinnerOptions.add("Fund");
        spinnerOptions.addAll(parsedMembersList);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaidBy.setAdapter(adapter);
    }

    // --- UPDATED: Beautifully Styled Dynamic Checkboxes ---
    private void generateDynamicMembersCheckboxes() {
        layoutCheckboxContainer.removeAllViews();
        activeCheckBoxesReferences.clear();

        for (String memberName : parsedMembersList) {
            CheckBox cb = new CheckBox(this);
            cb.setText(memberName);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            cb.setTextColor(android.graphics.Color.parseColor("#0D1A39")); // Darker text for readability
            cb.setPadding(12, 12, 12, 12); // Internal padding inside the box
            cb.setChecked(true); // Checked by default

            // Create a custom drawable for the white, rounded background with a subtle border
            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setColor(android.graphics.Color.parseColor("#E6E6E6"));
            borderDrawable.setCornerRadius(12f); // Rounded corners
            borderDrawable.setStroke(1, android.graphics.Color.parseColor("#666666")); // Subtle gray border
            cb.setBackground(borderDrawable);

            // Listen for changes to update Live Split calculation
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> calculateAndDisplaySplit());

            // Setup Layout Parameters for the Grid
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; // Equal width
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); // 1f weight

            // Add margins around each box so they don't touch
            params.setMargins(8, 12, 8, 12);
            cb.setLayoutParams(params);

            layoutCheckboxContainer.addView(cb);
            activeCheckBoxesReferences.add(cb);
        }
    }

    private void executeExpenseValidationPipeline() {
        String purpose = edtPurpose.getText().toString().trim();
        String amountRaw = edtAmount.getText().toString().trim();
        if (purpose.isEmpty() || amountRaw.isEmpty()) return;

        double totalAmount = Double.parseDouble(amountRaw);
        String selectedPayer = spinnerPaidBy.getSelectedItem().toString();

        if ("Fund".equals(selectedPayer)) {
            double currentFundBalance = dbHelper.getFundBalance(currentTripId);
            double oldAmount = 0.0;
            if (isEditMode) {
                try (Cursor c = dbHelper.getExpenseById(editTransactionId)) {
                    if (c.moveToFirst()) {
                        oldAmount = c.getDouble(c.getColumnIndexOrThrow("expense_amount"));
                    }
                }
            }

            double effectiveBalance = currentFundBalance + (isEditMode ? oldAmount : 0.0);

            if (totalAmount > effectiveBalance) {
                Toast.makeText(this, "Insufficient Fund Balance! Available: ₹" + String.format(Locale.US, "%.2f", effectiveBalance), Toast.LENGTH_LONG).show();
                return;
            }
        }

        ArrayList<String> participatingMembersList = new ArrayList<>();
        for (CheckBox cb : activeCheckBoxesReferences) {
            if (cb.isChecked()) participatingMembersList.add(cb.getText().toString().trim());
        }

        if (participatingMembersList.isEmpty()) {
            Toast.makeText(this, "Select at least one member!", Toast.LENGTH_SHORT).show();
            return;
        }

        String joinedSharedWithText = TextUtils.join(", ", participatingMembersList);
        String dateStr = etExpenseDate.getText().toString();

        if (isEditMode) {
            dbHelper.updateExpense(editTransactionId, purpose, totalAmount, selectedPayer, joinedSharedWithText, dateStr);
            Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show();
        } else {
            dbHelper.insertExpense(currentTripId, purpose, totalAmount, selectedPayer, joinedSharedWithText, dateStr);
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}