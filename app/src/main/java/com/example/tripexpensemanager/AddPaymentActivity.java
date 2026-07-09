package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import android.content.Intent;
// Rounded corner dialog
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
// Rounded corner dialog End
import android.widget.ImageButton;

public class AddPaymentActivity extends AppCompatActivity {

    private static final String TAG = "AddPaymentActivity";

    private Spinner spinnerPaymentBy;
    private EditText edtPaymentDate, edtPaymentAmount;

    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private TripDatabaseHelper dbHelper;
    private String currentTripId;
    private final ArrayList<String> parsedMembersList = new ArrayList<>();

    // --- NEW: Edit Mode Flags ---
    private boolean isEditMode = false;
    private int editTransactionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_payment);

        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        spinnerPaymentBy = findViewById(R.id.spinner_payment_by);
        edtPaymentDate = findViewById(R.id.edt_payment_date);
        edtPaymentAmount = findViewById(R.id.edt_payment_amount);
        TextView txtHeading = findViewById(R.id.txt_payment_heading);
        Button btnSavePayment = findViewById(R.id.btn_save_payment);

        edtPaymentDate.setShowSoftInputOnFocus(false);

        dbHelper = new TripDatabaseHelper(this);
        edtPaymentDate.setText(dateFormatter.format(calendar.getTime()));
        edtPaymentDate.setOnClickListener(v -> showDatePicker());

        extractIncomingIntentData();

        // --- NEW: Load Data if Editing ---
        if (isEditMode && editTransactionId != -1) {
            txtHeading.setText(R.string.edit_payment);
            btnSavePayment.setText(R.string.update_payment);
            loadExistingPaymentData();
        }

        btnSavePayment.setOnClickListener(v -> executePaymentValidationPipeline());
    }

    @SuppressWarnings("unchecked")
    private void loadExistingPaymentData() {
        try (Cursor c = dbHelper.getPaymentById(editTransactionId)) {
            if (c.moveToFirst()) {
                edtPaymentAmount.setText(String.valueOf(c.getDouble(c.getColumnIndexOrThrow("payment_amount"))));
                edtPaymentDate.setText(c.getString(c.getColumnIndexOrThrow("payment_date")));

                String paidBy = c.getString(c.getColumnIndexOrThrow("payment_by"));
                ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerPaymentBy.getAdapter();

                if (adapter != null && paidBy != null) {
                    for (int i = 0; i < adapter.getCount(); i++) {
                        if (paidBy.equals(adapter.getItem(i))) {
                            spinnerPaymentBy.setSelection(i);
                            break;
                        }
                    }
                }
            }
        }
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

        // Rounded corner dialog
        Window window = datePickerDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );

            window.setBackgroundDrawableResource(
                    R.drawable.bg_date_picker_dialog
            );
        }
        // Rounded corner dialog End

        Button positiveButton = datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            View buttonPanel = (View) positiveButton.getParent();
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
                    edtPaymentDate.setText(dateFormatter.format(calendar.getTime()));
                },
                year, month, day
        );
    }

    private void extractIncomingIntentData() {
        if (getIntent() != null) {
            currentTripId = getIntent().getStringExtra("TRIP_ID");
            String rawMembersStr = getIntent().getStringExtra("TRIP_MEMBERS");

            isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);
            editTransactionId = getIntent().getIntExtra("TRANS_ID", -1);

            Log.d(TAG, "Initializing payment ledger environment for unique key: " + currentTripId);

            // Fetch the compiled list using our helper method
            ArrayList<String> allMembers = compileCompleteMemberList(rawMembersStr);

            parsedMembersList.clear();
            parsedMembersList.addAll(allMembers);

            if (parsedMembersList.isEmpty()) {
                Toast.makeText(this, "Error: No trip members found context packet!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Collections.sort(parsedMembersList);
                populatePaymentBySpinner();
            }
        }
    }

    // --- THIS IS THE METHOD THAT WAS MISSING ---
    private ArrayList<String> compileCompleteMemberList(String rawMembersStr) {
        ArrayList<String> allMembers = new ArrayList<>();

        // 1. Add currently active members from intent
        if (rawMembersStr != null && !rawMembersStr.trim().isEmpty()) {
            for (String name : rawMembersStr.split(",")) {
                if (!name.trim().isEmpty() && !allMembers.contains(name.trim())) {
                    allMembers.add(name.trim());
                }
            }
        }

        // 2. Add historical members if in edit mode
        if (isEditMode) {
            for (String historical : getHistoricalMembers()) {
                if (!allMembers.contains(historical)) {
                    allMembers.add(historical);
                }
            }
        }

        return allMembers;
    }

    private void populatePaymentBySpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, parsedMembersList) {
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
        spinnerPaymentBy.setAdapter(adapter);
    }

    private void executePaymentValidationPipeline() {
        String amountRaw = edtPaymentAmount.getText().toString().trim();
        String paymentDateStr = edtPaymentDate.getText().toString().trim();

        if (amountRaw.isEmpty() || paymentDateStr.isEmpty()) {
            Toast.makeText(this, "All fields are mandatory!", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalAmount;
        try {
            totalAmount = Double.parseDouble(amountRaw);
            if (totalAmount <= 0) {
                Toast.makeText(this, "Payment amount must be greater than zero!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid numeric content structural format!", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedPayer = spinnerPaymentBy.getSelectedItem().toString();

        if (isEditMode) {
            boolean success = dbHelper.updatePayment(editTransactionId, selectedPayer, paymentDateStr, totalAmount);
            if (success) {
                Toast.makeText(this, "Payment updated successfully!", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(this, CompleteLedgerActivity.class);
                intent.putExtra("TRIP_ID", currentTripId);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Update failed!", Toast.LENGTH_SHORT).show();
            }
        } else {
            long insertedRowId = dbHelper.insertPayment(currentTripId, selectedPayer, paymentDateStr, totalAmount);
            if (insertedRowId != -1) {
                Toast.makeText(this, "Payment of ₹" + amountRaw + " successfully registered!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Critical database failure!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // --- HISTORICAL MEMBER HELPER METHODS ---
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
            android.util.Log.e("AddPaymentActivity", "Error fetching historical members: " + e.getMessage());
        }
    }
}