package com.example.tripexpensemanager;

public class Transaction {
    public int id;
    public String date, timestamp, purpose;
    public double debit, credit;

    public Transaction(int id, String date, String timestamp, String purpose, double debit, double credit) {
        this.id = id;
        this.date = date;
        this.timestamp = timestamp;
        this.purpose = purpose;
        this.debit = debit;
        this.credit = credit;
    }
}