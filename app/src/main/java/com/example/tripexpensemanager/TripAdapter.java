package com.example.tripexpensemanager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final ArrayList<TripModel> tripList;
    private final OnTripActionListener actionListener;
    private final boolean isMinimalMode;

    private static final int VIEW_TYPE_FULL = 0;
    private static final int VIEW_TYPE_MINIMAL = 1;

    public interface OnTripActionListener {
        void onEditClick(TripModel trip);
        void onDeleteClick(TripModel trip);
        void onAddExpenseClick(TripModel trip);
        void onAddPaymentClick(TripModel trip);
        void onPinToggleClick(TripModel trip, int position);
        void onTripItemClick(TripModel trip);
    }

    // Constructor: Used by both Dashboard and TripListActivity
    public TripAdapter(ArrayList<TripModel> tripList, boolean isMinimalMode, OnTripActionListener actionListener) {
        this.tripList = tripList;
        this.isMinimalMode = isMinimalMode;
        this.actionListener = actionListener;
    }

    @Override
    public int getItemViewType(int position) {
        return isMinimalMode ? VIEW_TYPE_MINIMAL : VIEW_TYPE_FULL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MINIMAL) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip_minimal, parent, false);
            return new MinimalTripViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
            return new FullTripViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TripModel trip = tripList.get(position);
        Context context = holder.itemView.getContext();

        // ==========================================
        // 1. CALCULATE ROLE & BADGE STYLE ONCE
        // ==========================================
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserEmail = (user != null && user.getEmail() != null) ? user.getEmail() : "";
        String currentUserRole = "Viewer";

        if (!currentUserEmail.isEmpty()) {
            if (currentUserEmail.equalsIgnoreCase(trip.getOwnerEmail())) {
                currentUserRole = "Admin";
            } else if (trip.getMemberDetails() != null) {
                for (TripMember member : trip.getMemberDetails()) {
                    if (currentUserEmail.equalsIgnoreCase(member.getEmailId())) {
                        currentUserRole = member.getRole() != null ? member.getRole() : "Viewer";
                        break;
                    }
                }
            }
        }

        // Setup Badge Background & Colors
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int hPadding = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 10, metrics);
        int vPadding = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 2, metrics);
        float radius = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 4, metrics);

        android.graphics.drawable.GradientDrawable roleBg = new android.graphics.drawable.GradientDrawable();
        roleBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        roleBg.setCornerRadius(radius);

        int roleTextColor;
        if ("Admin".equalsIgnoreCase(currentUserRole)) {
            roleBg.setColor(0xFF85022E);
            roleTextColor = 0xFFFAF7F7;
        } else if ("Editor".equalsIgnoreCase(currentUserRole)) {
            roleBg.setColor(0xFF3e8914);
            roleTextColor = 0xFFF5FFF6;
        } else {
            roleBg.setColor(0xFF2f4550);
            roleTextColor = 0xFFe9ecef;
        }

        // ==========================================
        // 2. APPLY TO THE CORRECT LAYOUT
        // ==========================================
        if (holder.getItemViewType() == VIEW_TYPE_MINIMAL) {

            MinimalTripViewHolder minHolder = (MinimalTripViewHolder) holder;

            // Serial Number
            minHolder.txtSlNo.setText((position + 1) + ".");

            // 1. Destination (Only the destination, no heading)
            String dest = (trip.getDestination() != null && !trip.getDestination().trim().isEmpty()) ? trip.getDestination() : "Unknown Destination";
            minHolder.txtDestination.setText(dest);

            // 2. Date and Balance
            minHolder.txtDate.setText(trip.getStartDate() != null ? trip.getStartDate() : "TBD");
            minHolder.txtBalance.setText(String.format(Locale.US, "Bal: ₹%.2f", trip.getFundBalance()));

            // 3. Expense and Payment
            minHolder.txtExpense.setText("Exp: " + context.getString(R.string.fmt_dash_currency_rupees, trip.getTotalExpenses()));
            minHolder.txtPayment.setText("Pay: " + context.getString(R.string.fmt_dash_currency_rupees, trip.getTotalPayments()));

            // 4. Apply Role Badge
            minHolder.txtRoleBadge.setText(currentUserRole);
            minHolder.txtRoleBadge.setTextColor(roleTextColor);
            minHolder.txtRoleBadge.setBackground(roleBg);
            minHolder.txtRoleBadge.setPadding(hPadding, vPadding, hPadding, vPadding);

            // Switch Logic (Remove listener first to prevent recycling issues)
            minHolder.switchPinToggle.setOnCheckedChangeListener(null);
            minHolder.switchPinToggle.setChecked(trip.getIsPinnedState() == 1);
            minHolder.switchPinToggle.setOnCheckedChangeListener((btn, isChecked) ->
                    actionListener.onPinToggleClick(trip, position)
            );

            minHolder.itemView.setOnClickListener(v -> actionListener.onTripItemClick(trip));

        } else {

            FullTripViewHolder fullHolder = (FullTripViewHolder) holder;

            fullHolder.txtTotalExpense.setText(context.getString(R.string.fmt_dash_currency_rupees, trip.getTotalExpenses()));
            fullHolder.txtTotalReceived.setText(context.getString(R.string.fmt_dash_currency_rupees, trip.getTotalPayments()));
            fullHolder.txtFundBalance.setText(String.format(Locale.US, "₹%.2f", trip.getFundBalance()));

            // Apply Role Badge
            fullHolder.txtRoleBadge.setText(currentUserRole);
            fullHolder.txtRoleBadge.setTextColor(roleTextColor);
            fullHolder.txtRoleBadge.setBackground(roleBg);
            fullHolder.txtRoleBadge.setPadding(hPadding, vPadding, hPadding, vPadding);
            // Pin State text logic
            if (trip.getIsPinnedState() == 1) {
                fullHolder.txtTripName.setText(context.getString(R.string.fmt_item_name_pinned_sequential, (position + 1), trip.getDestination()));
                fullHolder.btnPin.setText(context.getString(R.string.action_state_unpin));
                fullHolder.btnPin.setTextColor(0xFF2E7D32);
            } else {
                fullHolder.txtTripName.setText(context.getString(R.string.fmt_item_name_sequential, (position + 1), trip.getDestination()));
                fullHolder.btnPin.setText(context.getString(R.string.action_state_pin));
                fullHolder.btnPin.setTextColor(0xFFC85A00);
            }

            // Listeners
            fullHolder.itemView.setOnClickListener(v -> actionListener.onTripItemClick(trip));
            fullHolder.btnPin.setOnClickListener(v -> actionListener.onPinToggleClick(trip, position));
            fullHolder.btnEdit.setOnClickListener(v -> actionListener.onEditClick(trip));
            fullHolder.btnDelete.setOnClickListener(v -> actionListener.onDeleteClick(trip));
            fullHolder.btnAddExpense.setOnClickListener(v -> actionListener.onAddExpenseClick(trip));
            fullHolder.btnAddPayment.setOnClickListener(v -> actionListener.onAddPaymentClick(trip));
        }
    }

    @Override
    public int getItemCount() { return tripList.size(); }

    public static class FullTripViewHolder extends RecyclerView.ViewHolder {
        TextView txtTripName, txtFundBalance;
        TextView txtTotalExpense, txtTotalReceived;
        TextView btnPin, btnEdit, btnDelete, txtRoleBadge;
        MaterialButton btnAddExpense, btnAddPayment;

        public FullTripViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTripName = itemView.findViewById(R.id.txt_item_trip_name);
            txtFundBalance = itemView.findViewById(R.id.txt_item_fund_balance);
            txtTotalExpense = itemView.findViewById(R.id.txt_item_total_expense);
            txtTotalReceived = itemView.findViewById(R.id.txt_item_total_received);
            btnPin = itemView.findViewById(R.id.btn_item_pin);
            btnAddExpense = itemView.findViewById(R.id.btn_item_add_expense);
            btnAddPayment = itemView.findViewById(R.id.btn_item_add_payment);
            txtRoleBadge = itemView.findViewById(R.id.txt_item_role_badge);
        }
    }

    public static class MinimalTripViewHolder extends RecyclerView.ViewHolder {
        TextView txtSlNo, txtDestination, txtDate, txtBalance, txtExpense, txtPayment, txtRoleBadge;
        SwitchCompat switchPinToggle;

        public MinimalTripViewHolder(@NonNull View itemView) {
            super(itemView);
            txtSlNo = itemView.findViewById(R.id.txt_sl_no);
            txtDestination = itemView.findViewById(R.id.txt_destination);
            txtDate = itemView.findViewById(R.id.txt_date);
            txtBalance=itemView.findViewById(R.id.txt_balance);
            txtExpense = itemView.findViewById(R.id.txt_expense);
            txtPayment=itemView.findViewById(R.id.txt_payment);
            txtRoleBadge = itemView.findViewById(R.id.txt_role_badge);
            switchPinToggle = itemView.findViewById(R.id.switch_pin_toggle);
        }
    }
}