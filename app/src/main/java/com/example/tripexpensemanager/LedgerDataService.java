package com.example.tripexpensemanager;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LedgerDataService {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    public interface LedgerCallback {
        void onResult(List<LedgerEntry> entries);
        void onError(Exception e);
    }

    public void fetchUnifiedLedger(String tripId, LedgerCallback callback) {
        Task<QuerySnapshot> expenseTask = db.collection("Trips").document(tripId).collection("Expenses").get();
        Task<QuerySnapshot> paymentTask = db.collection("Trips").document(tripId).collection("Payments").get();

        Tasks.whenAllSuccess(expenseTask, paymentTask).addOnSuccessListener(results -> {
            List<LedgerEntry> allEntries = new ArrayList<>();

            // 1. Process Expenses
            QuerySnapshot expenseSnap = (QuerySnapshot) results.get(0);
            for (DocumentSnapshot doc : expenseSnap.getDocuments()) {
                Double rawAmount = doc.getDouble("amount");
                double safeAmount = (rawAmount != null) ? rawAmount : 0.0;

                // Backwards compatibility: If status doesn't exist on old docs, assume APPROVED
                String status = doc.getString("status");
                if (status == null) status = "APPROVED";

                allEntries.add(new LedgerEntry(
                        doc.getId(), "Expense", doc.getString("purpose"),
                        safeAmount, doc.getString("date"),
                        doc.getString("paidBy"), doc.getString("sharedWith"), status
                ));
            }

            // 2. Process Payments
            QuerySnapshot paymentSnap = (QuerySnapshot) results.get(1);
            for (DocumentSnapshot doc : paymentSnap.getDocuments()) {
                Double rawAmount = doc.getDouble("amount");
                double safeAmount = (rawAmount != null) ? rawAmount : 0.0;

                // Backwards compatibility: If status doesn't exist on old docs, assume APPROVED
                String status = doc.getString("status");
                if (status == null) status = "APPROVED";

                allEntries.add(new LedgerEntry(
                        doc.getId(), "Payment", "Payment by " + doc.getString("paymentBy"),
                        safeAmount, doc.getString("date"),
                        doc.getString("paymentBy"), null, status
                ));
            }

            // 3. Sort Chronologically (Crash-Proof version)
            allEntries.sort((e1, e2) -> {
                try {
                    String date1Str = e1.getDate();
                    String date2Str = e2.getDate();

                    if (date1Str == null && date2Str == null) return 0;
                    if (date1Str == null) return -1;
                    if (date2Str == null) return 1;

                    Date d1 = dateFormat.parse(date1Str);
                    Date d2 = dateFormat.parse(date2Str);

                    if (d1 != null && d2 != null) {
                        return d1.compareTo(d2);
                    }
                } catch (ParseException e) {
                    return 0;
                }
                return 0;
            });

            callback.onResult(allEntries);
        }).addOnFailureListener(callback::onError);
    }
}