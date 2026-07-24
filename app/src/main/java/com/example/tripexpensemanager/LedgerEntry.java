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
    private final String status;

    // NEW: Audit Trail Fields
    private final String addedBy;
    private final String addedOn;
    private final String approvedOn;

    public LedgerEntry(String transId, String type, String purpose, double amount, String date, String paidBy, String sharedWith, String status, String addedBy, String addedOn, String approvedOn) {
        this.transId = transId;
        this.type = type;
        this.purpose = purpose;
        this.amount = amount;
        this.date = date;
        this.paidBy = paidBy;
        this.sharedWith = sharedWith;
        this.status = status;
        this.addedBy = addedBy;
        this.addedOn = addedOn;
        this.approvedOn = approvedOn;
    }

    // Getters
    public String getTransId() { return transId; }
    public String getType() { return type; }
    public String getPurpose() { return purpose; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getPaidBy() { return paidBy; }
    public String getSharedWith() { return sharedWith; }
    public String getStatus() { return status; }

    // NEW Getters
    public String getAddedBy() { return addedBy; }
    public String getAddedOn() { return addedOn; }
    public String getApprovedOn() { return approvedOn; }

    public boolean isExpense() { return "Expense".equals(type); }
}