package com.example.tripexpensemanager;

public class Transaction {
    public String date, purpose;
    public double debit, credit;

    public Transaction(String date, String purpose, double debit, double credit) {
        this.date = date;
        this.purpose = purpose;
        this.debit = debit;
        this.credit = credit;
    }
}