package com.example.tripexpensemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class LedgerAdapter extends RecyclerView.Adapter<LedgerAdapter.ViewHolder> {

    private final List<Transaction> transactionList;

    public LedgerAdapter(List<Transaction> transactionList) {
        this.transactionList = transactionList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ledger_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction t = transactionList.get(position);
        holder.date.setText(t.date);
        holder.purpose.setText(t.purpose);
        holder.debit.setText(t.debit > 0 ? String.format(Locale.US, "%.2f", t.debit) : "-");
        holder.credit.setText(t.credit > 0 ? String.format(Locale.US, "%.2f", t.credit) : "-");
    }

    @Override
    public int getItemCount() { return transactionList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView date, purpose, debit, credit;
        public ViewHolder(View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.txt_row_date);
            purpose = itemView.findViewById(R.id.txt_row_purpose);
            debit = itemView.findViewById(R.id.txt_row_debit);
            credit = itemView.findViewById(R.id.txt_row_credit);
        }
    }
}