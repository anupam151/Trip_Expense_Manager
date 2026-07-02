package com.example.tripexpensemanager;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MemberLedgerActivity extends AppCompatActivity {

    private String memberName, tripId;
    private TripDatabaseHelper dbHelper;
    private final List<Transaction> transactionList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_ledger);

        memberName = getIntent().getStringExtra("MEMBER_NAME");
        tripId = getIntent().getStringExtra("TRIP_ID");
        dbHelper = new TripDatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        TextView txtName = findViewById(R.id.txt_ledger_member_name);
        txtName.setText(memberName);

        RecyclerView recyclerView = findViewById(R.id.recycler_transactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadLedgerData();
    }

    private void loadLedgerData() {
        transactionList.clear();
        double totalDebit = 0;
        double totalCredit = 0;

        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT expense_date, expense_purpose, expense_amount, expense_paid_by, expense_shared_with FROM expenses WHERE expense_trip_id = ?",
                new String[]{tripId})) {

            while (c.moveToNext()) {
                String date = c.getString(0);
                String purpose = c.getString(1);
                double fullAmount = c.getDouble(2);
                String paidBy = c.getString(3);
                String sharedWith = c.getString(4);

                String[] members = sharedWith.split(",");
                double share = fullAmount / members.length;

                // logic: If member is the payer AND a participant, merge into one row
                if (paidBy.equals(memberName) && sharedWith.contains(memberName)) {
                    transactionList.add(new Transaction(date, purpose, share, fullAmount));
                    totalDebit += share;
                    totalCredit += fullAmount;
                } else if (sharedWith.contains(memberName)) {
                    transactionList.add(new Transaction(date, purpose, share, 0));
                    totalDebit += share;
                } else if (paidBy.equals(memberName)) {
                    transactionList.add(new Transaction(date, purpose, 0, fullAmount));
                    totalCredit += fullAmount;
                }
            }
        }

        // Add Payments
        try (Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT payment_date, payment_amount FROM payments WHERE payment_trip_id = ? AND payment_by = ?",
                new String[]{tripId, memberName})) {
            while (c.moveToNext()) {
                double amount = c.getDouble(1);
                transactionList.add(new Transaction(c.getString(0), "Cash Settlement", 0, amount));
                totalCredit += amount;
            }
        }

        // Update UI
        ((TextView) findViewById(R.id.txt_total_expenses)).setText(String.format(Locale.US, "₹%.2f", totalDebit));
        ((TextView) findViewById(R.id.txt_total_payments)).setText(String.format(Locale.US, "₹%.2f", totalCredit));
        ((TextView) findViewById(R.id.txt_footer_total_debit)).setText(String.format(Locale.US, "₹%.2f", totalDebit));
        ((TextView) findViewById(R.id.txt_footer_total_credit)).setText(String.format(Locale.US, "₹%.2f", totalCredit));
        ((TextView) findViewById(R.id.txt_transaction_count)).setText(String.format(Locale.US, "%d Transactions", transactionList.size()));

        double balance = totalCredit - totalDebit;
        TextView txtBalance = findViewById(R.id.txt_ledger_balance);
        txtBalance.setText(String.format(Locale.US, "₹%.2f", Math.abs(balance)));
        txtBalance.setTextColor(balance < 0 ? Color.parseColor("#85022E") : Color.parseColor("#2E7D32"));

        RecyclerView recyclerView = findViewById(R.id.recycler_transactions);
        recyclerView.setAdapter(new LedgerAdapter(transactionList));
    }
}