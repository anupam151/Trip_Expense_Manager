package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddExpenseActivity extends AppCompatActivity {

    private EditText edtPurpose, edtAmount;
    private EditText etExpenseDate;
    private Spinner spinnerPaidBy;
    private GridLayout layoutCheckboxContainer;

    private TextView txtSelectedCountPreview, txtSplitAmountPreview;

    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private FirebaseFirestore db;
    private String currentTripId;
    private String tripOwnerEmail = ""; // NEW: Stores Admin email
    private final ArrayList<String> parsedMembersList = new ArrayList<>();
    private final ArrayList<CheckBox> activeCheckBoxesReferences = new ArrayList<>();

    private boolean isEditMode = false;
    private String editExpenseId = null;
    private double oldAmount = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        db = FirebaseFirestore.getInstance();

        edtPurpose = findViewById(R.id.edt_expense_purpose);
        edtAmount = findViewById(R.id.edt_expense_amount);
        etExpenseDate = findViewById(R.id.et_expense_date);
        spinnerPaidBy = findViewById(R.id.spinner_paid_by);
        layoutCheckboxContainer = findViewById(R.id.layout_checkbox_container);
        TextView txtHeading = findViewById(R.id.txt_expense_heading);
        Button btnSaveExpense = findViewById(R.id.btn_save_expense);

        txtSelectedCountPreview = findViewById(R.id.txt_selected_count_preview);
        txtSplitAmountPreview = findViewById(R.id.txt_split_amount_preview);

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

        edtPurpose.requestFocus();
        edtPurpose.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edtPurpose, InputMethodManager.SHOW_IMPLICIT);
        }, 200);

        etExpenseDate.setShowSoftInputOnFocus(false);
        etExpenseDate.setText(dateFormatter.format(calendar.getTime()));
        etExpenseDate.setOnClickListener(v -> showDatePicker());

        extractIncomingIntentData();

        if (isEditMode && editExpenseId != null) {
            txtHeading.setText(R.string.edit_expense);
            btnSaveExpense.setText(R.string.update_expense);
            loadExistingExpenseData();
        } else {
            txtHeading.setText(R.string.add_new_expense);
        }

        btnSaveExpense.setOnClickListener(v -> executeExpenseValidationPipeline());

        calculateAndDisplaySplit();
    }

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
            editExpenseId = getIntent().getStringExtra("TRANS_ID");

            if (currentTripId != null) {
                db.collection("Trips").document(currentTripId).get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        tripOwnerEmail = doc.getString("ownerEmail");
                    }
                });
            }

            List<String> allMembers = new ArrayList<>();
            String rawMembersStr = getIntent().getStringExtra("TRIP_MEMBERS");
            if (rawMembersStr != null && !rawMembersStr.trim().isEmpty()) {
                for (String name : rawMembersStr.split(",")) {
                    if (!name.trim().isEmpty() && !allMembers.contains(name.trim())) {
                        allMembers.add(name.trim());
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

    @SuppressWarnings("unchecked")
    private void loadExistingExpenseData() {
        if (editExpenseId == null || editExpenseId.isEmpty()) return;

        db.collection("Trips").document(currentTripId).collection("Expenses").document(editExpenseId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        edtPurpose.setText(doc.getString("purpose"));
                        Double amt = doc.getDouble("amount");
                        if (amt != null) {
                            oldAmount = amt;
                            edtAmount.setText(String.valueOf(amt));
                        }
                        etExpenseDate.setText(doc.getString("date"));

                        String paidBy = doc.getString("paidBy");
                        String sharedWith = doc.getString("sharedWith");

                        boolean listUpdated = false;
                        if (paidBy != null && !paidBy.equals("Fund") && !parsedMembersList.contains(paidBy)) {
                            parsedMembersList.add(paidBy);
                            listUpdated = true;
                        }
                        if (sharedWith != null) {
                            for (String sw : sharedWith.split(",\\s*")) {
                                if (!sw.trim().isEmpty() && !parsedMembersList.contains(sw.trim())) {
                                    parsedMembersList.add(sw.trim());
                                    listUpdated = true;
                                }
                            }
                        }

                        if (listUpdated) {
                            Collections.sort(parsedMembersList);
                            populatePaidBySpinner();
                            generateDynamicMembersCheckboxes();
                        }

                        if (paidBy != null && spinnerPaidBy.getAdapter() instanceof ArrayAdapter) {
                            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerPaidBy.getAdapter();
                            for (int i = 0; i < adapter.getCount(); i++) {
                                if (paidBy.equals(adapter.getItem(i))) {
                                    spinnerPaidBy.setSelection(i);
                                    break;
                                }
                            }
                        }

                        if (sharedWith != null) {
                            List<String> sharedList = Arrays.asList(sharedWith.split(",\\s*"));
                            for (CheckBox cb : activeCheckBoxesReferences) {
                                cb.setChecked(sharedList.contains(cb.getText().toString().trim()));
                            }
                        }

                        calculateAndDisplaySplit();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load expense data", Toast.LENGTH_SHORT).show());
    }

    private void showDatePicker() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            currentFocus.clearFocus();
        }

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

    private void generateDynamicMembersCheckboxes() {
        layoutCheckboxContainer.removeAllViews();
        activeCheckBoxesReferences.clear();

        for (String memberName : parsedMembersList) {
            CheckBox cb = new CheckBox(this);
            cb.setText(memberName);
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            cb.setTextColor(android.graphics.Color.parseColor("#0D1A39"));
            cb.setPadding(12, 12, 12, 12);
            cb.setChecked(true);

            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setColor(android.graphics.Color.parseColor("#E6E6E6"));
            borderDrawable.setCornerRadius(12f);
            borderDrawable.setStroke(1, android.graphics.Color.parseColor("#666666"));
            cb.setBackground(borderDrawable);

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> calculateAndDisplaySplit());

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
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

        // Safely extract email with a fallback
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserEmail = (user != null && user.getEmail() != null) ? user.getEmail() : "Unknown";

        // Safe comparison: Checks if tripOwnerEmail is not null BEFORE comparing
        boolean isAdmin = tripOwnerEmail != null && tripOwnerEmail.equalsIgnoreCase(currentUserEmail);

        String status = isAdmin ? "ADMIN_ADDED" : "PENDING";

        // Generate Date & Time: DD MMM YY, hh:mm am/pm
        String addedOn = new SimpleDateFormat("dd MMM yy, hh:mm a", Locale.US).format(Calendar.getInstance().getTime());
        String approvedOn = isAdmin ? "NA" : "Pending";

        Map<String, Object> expenseData = new HashMap<>();
        expenseData.put("purpose", purpose);
        expenseData.put("amount", totalAmount);
        expenseData.put("date", dateStr);
        expenseData.put("paidBy", selectedPayer);
        expenseData.put("sharedWith", joinedSharedWithText);
        expenseData.put("status", status);
        expenseData.put("addedBy", currentUserEmail); // Audit Trail
        expenseData.put("addedOn", addedOn);          // Audit Trail
        expenseData.put("approvedOn", approvedOn);    // Audit Trail

        if ("Fund".equals(selectedPayer)) {
            validateFundAndSave(expenseData, totalAmount);
        } else {
            saveToCloud(expenseData);
        }
    }

    private void validateFundAndSave(Map<String, Object> expenseData, double totalAmount) {
        db.collection("Trips").document(currentTripId).collection("Payments")
                .whereEqualTo("paymentTo", "Fund")
                .get(com.google.firebase.firestore.Source.CACHE)
                .addOnCompleteListener(task -> {
                    double totalPayments = 0.0;
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot ds : task.getResult()) {
                            Double amt = ds.getDouble("amount");
                            if (amt != null) totalPayments += amt;
                        }
                    }

                    final double finalTotalPayments = totalPayments;

                    db.collection("Trips").document(currentTripId).collection("Expenses")
                            .whereEqualTo("paidBy", "Fund")
                            .get(com.google.firebase.firestore.Source.CACHE)
                            .addOnCompleteListener(task2 -> {
                                double totalFundExpenses = 0.0;
                                if (task2.isSuccessful() && task2.getResult() != null) {
                                    for (DocumentSnapshot ds : task2.getResult()) {
                                        Double amt = ds.getDouble("amount");
                                        if (amt != null) totalFundExpenses += amt;
                                    }
                                }

                                double effectiveBalance = (finalTotalPayments - totalFundExpenses) + (isEditMode ? oldAmount : 0.0);

                                if (totalAmount > effectiveBalance) {
                                    Toast.makeText(this, "Insufficient Fund! Available: ₹" + String.format(Locale.US, "%.2f", effectiveBalance), Toast.LENGTH_LONG).show();
                                } else {
                                    saveToCloud(expenseData);
                                }
                            });
                });
    }

    private void saveToCloud(Map<String, Object> expenseData) {
        Log.d("AddExpenseActivity", "saveToCloud started...");

        if (isEditMode && editExpenseId != null) {
            db.collection("Trips").document(currentTripId).collection("Expenses").document(editExpenseId)
                    .set(expenseData)
                    .addOnSuccessListener(a -> {
                        Toast.makeText(this, "Expense Updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        } else {
            db.collection("Trips").document(currentTripId).collection("Expenses")
                    .add(expenseData)
                    .addOnSuccessListener(a -> {
                        Toast.makeText(this, "Expense Added!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }
}