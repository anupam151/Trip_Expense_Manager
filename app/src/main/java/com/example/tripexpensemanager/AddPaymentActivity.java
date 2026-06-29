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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import android.content.Intent;

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

        spinnerPaymentBy = findViewById(R.id.spinner_payment_by);
        edtPaymentDate = findViewById(R.id.edt_payment_date);
        edtPaymentAmount = findViewById(R.id.edt_payment_amount);
        Button btnSavePayment = findViewById(R.id.btn_save_payment);

        edtPaymentDate.setShowSoftInputOnFocus(false);

        dbHelper = new TripDatabaseHelper(this);
        edtPaymentDate.setText(dateFormatter.format(calendar.getTime()));
        edtPaymentDate.setOnClickListener(v -> showDatePicker());

        extractIncomingIntentData();

        // --- NEW: Load Data if Editing ---
        if (isEditMode && editTransactionId != -1) {
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

                // Fixed: Added null checks to prevent NullPointerException
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
            if (rawMembersStr != null && !rawMembersStr.trim().isEmpty()) {
                String[] splitNames = rawMembersStr.split(",");
                for (String name : splitNames) {
                    if (!name.trim().isEmpty()) {
                        parsedMembersList.add(name.trim());
                    }
                }
                Collections.sort(parsedMembersList);
                populatePaymentBySpinner();
            } else {
                Toast.makeText(this, "Error: No trip members found context packet!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
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

                // Return to ledger immediately
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
}