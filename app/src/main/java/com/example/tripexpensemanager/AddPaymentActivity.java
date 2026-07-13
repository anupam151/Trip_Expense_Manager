package com.example.tripexpensemanager;

import android.app.DatePickerDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
// Rounded corner dialog End
import android.widget.ImageButton;

// --- Firebase Imports ---
import com.google.firebase.firestore.FirebaseFirestore;

public class AddPaymentActivity extends AppCompatActivity {

    private static final String TAG = "AddPaymentActivity";

    private Spinner spinnerPaymentBy;
    private EditText edtPaymentDate, edtPaymentAmount;

    private final Calendar calendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    // --- Kept for Historical Member lookup ---
    private TripDatabaseHelper dbHelper;
    private String currentTripId;
    private final ArrayList<String> parsedMembersList = new ArrayList<>();

    // --- Firebase DB ---
    private FirebaseFirestore db;

    // --- Edit Mode Flags ---
    private boolean isEditMode = false;
    private Button btnSavePayment;
    private String editPaymentId = null; // Firestore uses String IDs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_payment);

        db = FirebaseFirestore.getInstance();
        dbHelper = new TripDatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        spinnerPaymentBy = findViewById(R.id.spinner_payment_by);
        edtPaymentDate = findViewById(R.id.edt_payment_date);
        edtPaymentAmount = findViewById(R.id.edt_payment_amount);
        TextView txtHeading = findViewById(R.id.txt_payment_heading);
        btnSavePayment = findViewById(R.id.btn_save_payment);

        edtPaymentDate.setShowSoftInputOnFocus(false);
        edtPaymentDate.setText(dateFormatter.format(calendar.getTime()));
        edtPaymentDate.setOnClickListener(v -> showDatePicker());

        extractIncomingIntentData();

        if (isEditMode && editPaymentId != null) {
            txtHeading.setText(R.string.edit_payment);
            btnSavePayment.setText(R.string.update_payment);
            loadExistingPaymentData();
        } else {
            txtHeading.setText(R.string.label_title_add_payment);
        }

        btnSavePayment.setOnClickListener(v -> executePaymentValidationPipeline());
    }

    private void loadExistingPaymentData() {
        if (editPaymentId == null) return;

        db.collection("Trips").document(currentTripId).collection("Payments").document(editPaymentId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        edtPaymentAmount.setText(String.valueOf(doc.getDouble("amount")));
                        edtPaymentDate.setText(doc.getString("date"));

                        String paidBy = doc.getString("paymentBy"); // Corresponds to payment_by in DB
                        @SuppressWarnings("unchecked")
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
                });
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
            editPaymentId = getIntent().getStringExtra("TRANS_ID");

            ArrayList<String> allMembers = compileCompleteMemberList(rawMembersStr);
            parsedMembersList.clear();
            parsedMembersList.addAll(allMembers);

            if (parsedMembersList.isEmpty()) {
                Toast.makeText(this, "Error: No members found!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Collections.sort(parsedMembersList);
                populatePaymentBySpinner();
            }
        }
    }

    private ArrayList<String> compileCompleteMemberList(String rawMembersStr) {
        ArrayList<String> allMembers = new ArrayList<>();
        if (rawMembersStr != null && !rawMembersStr.trim().isEmpty()) {
            for (String name : rawMembersStr.split(",")) {
                if (!name.trim().isEmpty() && !allMembers.contains(name.trim())) {
                    allMembers.add(name.trim());
                }
            }
        }
        if (isEditMode) {
            for (String historical : getHistoricalMembers()) {
                if (!allMembers.contains(historical)) allMembers.add(historical);
            }
        }
        return allMembers;
    }

    private void populatePaymentBySpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, parsedMembersList);
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
                Toast.makeText(this, "Amount must be > 0!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            return;
        }

        // 1. Lock the button so user can't click twice
        btnSavePayment.setEnabled(false);
        btnSavePayment.setText("Saving...");

        String selectedPayer = spinnerPaymentBy.getSelectedItem().toString();

        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("paymentBy", selectedPayer);
        paymentData.put("paymentTo", "Fund");
        paymentData.put("date", paymentDateStr);
        paymentData.put("amount", totalAmount);

        if (isEditMode && editPaymentId != null) {
            db.collection("Trips").document(currentTripId).collection("Payments").document(editPaymentId)
                    .set(paymentData)
                    .addOnFailureListener(e -> {
                        btnSavePayment.setEnabled(true);
                        btnSavePayment.setText(R.string.update_payment);
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            db.collection("Trips").document(currentTripId).collection("Payments")
                    .add(paymentData)
                    .addOnFailureListener(e -> {
                        btnSavePayment.setEnabled(true);
                        btnSavePayment.setText(R.string.label_title_add_payment);
                        Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }

        // 2. Close immediately (Offline or Online)
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
        finish();
    }

    // --- HISTORICAL MEMBER HELPER METHODS (Kept for local lookups) ---
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
            Log.e(TAG, "Historical lookup error: " + e.getMessage());
        }
    }
}