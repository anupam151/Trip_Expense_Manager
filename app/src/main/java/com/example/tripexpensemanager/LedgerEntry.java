package com.example.tripexpensemanager;

import java.io.Serializable;

public class LedgerEntry implements Serializable {
    private final String transId;
    private final String type; // "Expense" or "Payment"
    private final String purpose;
    private final double amount;
    private final String date;
    private final String paidBy;
    private final String sharedWith; // Only for Expenses
    private final String status; // NEW: Tracks Maker-Checker state

    public LedgerEntry(String transId, String type, String purpose, double amount, String date, String paidBy, String sharedWith, String status) {
        this.transId = transId;
        this.type = type;
        this.purpose = purpose;
        this.amount = amount;
        this.date = date;
        this.paidBy = paidBy;
        this.sharedWith = sharedWith;
        this.status = status;
    }

    // Getters
    public String getTransId() { return transId; }
    public String getType() { return type; }
    public String getPurpose() { return purpose; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getPaidBy() { return paidBy; }
    public String getSharedWith() { return sharedWith; }
    public String getStatus() { return status; } // NEW

    // Helper to identify quickly
    public boolean isExpense() { return "Expense".equals(type); }
}