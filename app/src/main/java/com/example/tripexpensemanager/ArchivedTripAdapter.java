package com.example.tripexpensemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

public class ArchivedTripAdapter extends RecyclerView.Adapter<ArchivedTripAdapter.ViewHolder> {

    private final List<DocumentSnapshot> tripsList;
    private final OnRestoreClickListener restoreClickListener;

    public interface OnRestoreClickListener {
        void onRestoreClick(DocumentSnapshot document);
    }

    public ArchivedTripAdapter(List<DocumentSnapshot> tripsList, OnRestoreClickListener restoreClickListener) {
        this.tripsList = tripsList;
        this.restoreClickListener = restoreClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_archived_trip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentSnapshot doc = tripsList.get(position);

        // 1. Set Serial Number
        holder.txtSlNo.setText(String.valueOf(position + 1));

        // 2. Set Destination (Fallback to Trip Name if Destination is empty)
        String destination = doc.getString("destination");
        String tripName = doc.getString("tripName");
        if (destination != null && !destination.trim().isEmpty()) {
            holder.txtDestination.setText(destination);
        } else {
            holder.txtDestination.setText(tripName != null ? tripName : "Unknown");
        }

        // 3. Set Date
        String startDate = doc.getString("startDate");
        holder.txtDate.setText(startDate != null ? "Departure: " + startDate : "Departure: N/A");

        // 4. Calculate the REAL Total Expense
        String tripId = doc.getId();

        // Show a loading text briefly while it calculates
        holder.txtExpense.setText("Total Expense: ...");

        TripFinanceCalculator.calculateFinances(tripId, new TripFinanceCalculator.FinanceResultListener() {
            @Override
            public void onStart() {
                // Background calculation started
            }

            @Override
            public void onResult(double totalExp, double totalRec, double fundBal) {
                // Update the UI with the actual calculated expense
                // Using String.format to add commas for large numbers (e.g., ₹12,500)
                holder.txtExpense.setText(String.format("Total Expense: ₹%,.0f", totalExp));
            }
        });

        // 5. Unarchive Button Logic
        holder.btnUnarchive.setOnClickListener(v -> restoreClickListener.onRestoreClick(doc));
    }

    @Override
    public int getItemCount() {
        return tripsList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtSlNo, txtDestination, txtDate, txtExpense;
        MaterialButton btnUnarchive;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtSlNo = itemView.findViewById(R.id.txt_archived_sl_no);
            txtDestination = itemView.findViewById(R.id.txt_archived_destination);
            txtDate = itemView.findViewById(R.id.txt_archived_date);
            txtExpense = itemView.findViewById(R.id.txt_archived_expense);
            btnUnarchive = itemView.findViewById(R.id.btn_unarchive_trip);
        }
    }
}