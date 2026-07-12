package com.example.tripexpensemanager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class TripFinanceCalculator {

    // Interface to send the calculated data back to the UI screen
    public interface FinanceResultListener {
        void onResult(double totalExpense, double totalReceived, double fundBalance);
    }

    // Universal method to calculate everything from one place
    public static void calculateFinances(String tripId, FinanceResultListener listener) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Trips").document(tripId).collection("Expenses").get().addOnSuccessListener(expSnaps -> {
            // Temporary variables for the first calculation loop
            double tempTotalExpense = 0.0;
            double tempExpensesPaidByFund = 0.0;
            double tempExpensesPaidByMembers = 0.0;

            for (QueryDocumentSnapshot exp : expSnaps) {
                double amount = getSafeAmount(exp);
                tempTotalExpense += amount; // 1. ALL Expenses

                String paidBy = exp.getString("paidBy");
                if ("Fund".equalsIgnoreCase(paidBy)) {
                    tempExpensesPaidByFund += amount; // Used by Fund
                } else {
                    tempExpensesPaidByMembers += amount; // Paid directly by Members
                }
            }

            // --- WARNING FIX: The Final Bridge ---
            // Java requires variables used in a nested callback to be "final" (locked in).
            final double finalTotalExpense = tempTotalExpense;
            final double finalExpensesPaidByFund = tempExpensesPaidByFund;
            final double finalExpensesPaidByMembers = tempExpensesPaidByMembers;

            db.collection("Trips").document(tripId).collection("Payments").get().addOnSuccessListener(paySnaps -> {
                double totalPayments = 0.0;
                for (QueryDocumentSnapshot pay : paySnaps) {
                    totalPayments += getSafeAmount(pay);
                }

                // --- YOUR EXACT CALCULATION FORMULAS ---

                // 2. Total Received: Add Payments + Expenses paid by members
                double totalReceived = totalPayments + finalExpensesPaidByMembers;

                // 3. Fund Balance: Add Payments - Expenses paid by Fund
                double fundBalance = totalPayments - finalExpensesPaidByFund;

                // Return the calculated data to whatever page requested it
                listener.onResult(finalTotalExpense, totalReceived, fundBalance);
            });
        });
    }

    // --- WARNING FIX: Removed 'field' parameter since we only calculate amounts ---
    // Prevents crashes if Firestore data is formatted weirdly
    private static double getSafeAmount(DocumentSnapshot doc) {
        Object obj = doc.get("amount");
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try { return Double.parseDouble((String) obj); } catch (Exception ignored) {}
        }
        return 0.0;
    }
}