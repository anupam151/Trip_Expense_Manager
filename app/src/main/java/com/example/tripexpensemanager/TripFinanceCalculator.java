package com.example.tripexpensemanager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

public class TripFinanceCalculator {

    // Interface with onStart to handle the "0" flicker problem
    public interface FinanceResultListener {
        void onStart();
        void onResult(double totalExpense, double totalReceived, double fundBalance);
    }

    public static void calculateFinances(String tripId, FinanceResultListener listener) {
        // 1. Tell the UI to show the loading spinner immediately
        listener.onStart();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 2. Fetch Expenses (Using DEFAULT to sync if online, cache if offline)
        db.collection("Trips").document(tripId).collection("Expenses")
                .get(Source.DEFAULT)
                .addOnCompleteListener(expTask -> {
                    double totalExpense = 0.0;
                    double expensesPaidByFund = 0.0;
                    double expensesPaidByMembers = 0.0;

                    if (expTask.isSuccessful() && expTask.getResult() != null) {
                        for (DocumentSnapshot exp : expTask.getResult()) {
                            double amount = getSafeAmount(exp);
                            totalExpense += amount;
                            String paidBy = exp.getString("paidBy");
                            if ("Fund".equalsIgnoreCase(paidBy)) {
                                expensesPaidByFund += amount;
                            } else {
                                expensesPaidByMembers += amount;
                            }
                        }
                    }

                    final double finalTotalExpense = totalExpense;
                    final double finalExpensesPaidByFund = expensesPaidByFund;
                    final double finalExpensesPaidByMembers = expensesPaidByMembers;

                    // 3. Chain Payment Fetch after Expenses are done
                    db.collection("Trips").document(tripId).collection("Payments")
                            .get(Source.DEFAULT)
                            .addOnCompleteListener(payTask -> {
                                double totalPayments = 0.0;
                                if (payTask.isSuccessful() && payTask.getResult() != null) {
                                    for (DocumentSnapshot pay : payTask.getResult()) {
                                        totalPayments += getSafeAmount(pay);
                                    }
                                }

                                // Final Math
                                double totalReceived = totalPayments + finalExpensesPaidByMembers;
                                double fundBalance = totalPayments - finalExpensesPaidByFund;

                                // 4. Return results (UI will hide spinner here)
                                listener.onResult(finalTotalExpense, totalReceived, fundBalance);
                            });
                });
    }

    private static double getSafeAmount(DocumentSnapshot doc) {
        Object obj = doc.get("amount");
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try { return Double.parseDouble((String) obj); } catch (Exception ignored) {}
        }
        return 0.0;
    }
}