package com.example.tripexpensemanager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private final ArrayList<TripModel> tripList;
    private final OnTripActionListener actionListener;

    public interface OnTripActionListener {
        void onEditClick(TripModel trip);
        void onDeleteClick(TripModel trip);
        void onAddExpenseClick(TripModel trip);
        void onAddPaymentClick(TripModel trip);
        void onPinToggleClick(TripModel trip, int position);
        void onTripItemClick(TripModel trip);
    }

    public TripAdapter(ArrayList<TripModel> tripList, OnTripActionListener actionListener) {
        this.tripList = tripList;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        TripModel trip = tripList.get(position);
        Context context = holder.itemView.getContext();

        holder.txtDestination.setText(context.getString(R.string.fmt_item_destination, trip.getDestination()));
        holder.txtMemberCount.setText(context.getString(R.string.fmt_item_member_count, trip.getMemberCount()));
        holder.txtStartDate.setText(context.getString(R.string.fmt_item_start_date, trip.getStartDate()));

        // --- UPDATED: Data now comes from the TripModel object directly ---
        // Ensure your TripModel class has fields for these and getters
        holder.txtTotalExpense.setText(context.getString(R.string.fmt_dash_currency_rupees, trip.getTotalExpenses()));
        holder.txtTotalReceived.setText(context.getString(R.string.fmt_dash_currency_rupees, trip.getTotalPayments()));
        holder.txtFundBalance.setText(String.format(Locale.US, "₹%.2f", trip.getFundBalance()));

        if (trip.getIsPinnedState() == 1) {
            holder.txtTripName.setText(context.getString(R.string.fmt_item_name_pinned_sequential, (position + 1), trip.getTripName()));
            holder.btnPin.setText(context.getString(R.string.action_state_unpin));
            holder.btnPin.setTextColor(0xFF2E7D32);
        } else {
            holder.txtTripName.setText(context.getString(R.string.fmt_item_name_sequential, (position + 1), trip.getTripName()));
            holder.btnPin.setText(context.getString(R.string.action_state_pin));
            holder.btnPin.setTextColor(0xFFC85A00);
        }

        // Click Listeners
        holder.itemView.setOnClickListener(v -> actionListener.onTripItemClick(trip));
        holder.btnPin.setOnClickListener(v -> actionListener.onPinToggleClick(trip, holder.getBindingAdapterPosition()));
        holder.btnEdit.setOnClickListener(v -> actionListener.onEditClick(trip));
        holder.btnDelete.setOnClickListener(v -> actionListener.onDeleteClick(trip));
        holder.btnAddExpense.setOnClickListener(v -> actionListener.onAddExpenseClick(trip));
        holder.btnAddPayment.setOnClickListener(v -> actionListener.onAddPaymentClick(trip));
    }

    @Override
    public int getItemCount() { return tripList.size(); }

    public static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView txtTripName, txtDestination, txtMemberCount, txtFundBalance, txtStartDate;
        TextView txtTotalExpense, txtTotalReceived;
        TextView btnPin, btnEdit, btnDelete;
        MaterialButton btnAddExpense, btnAddPayment;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTripName = itemView.findViewById(R.id.txt_item_trip_name);
            txtDestination = itemView.findViewById(R.id.txt_item_destination);
            txtMemberCount = itemView.findViewById(R.id.txt_item_member_count);
            txtFundBalance = itemView.findViewById(R.id.txt_item_fund_balance);
            txtStartDate = itemView.findViewById(R.id.txt_item_start_date);
            txtTotalExpense = itemView.findViewById(R.id.txt_item_total_expense);
            txtTotalReceived = itemView.findViewById(R.id.txt_item_total_received);
            btnPin = itemView.findViewById(R.id.btn_item_pin);
            btnEdit = itemView.findViewById(R.id.btn_item_edit);
            btnDelete = itemView.findViewById(R.id.btn_item_delete);
            btnAddExpense = itemView.findViewById(R.id.btn_item_add_expense);
            btnAddPayment = itemView.findViewById(R.id.btn_item_add_payment);
        }
    }
}