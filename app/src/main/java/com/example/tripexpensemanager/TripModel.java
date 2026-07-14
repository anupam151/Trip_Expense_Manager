package com.example.tripexpensemanager;

/**
 * Data model for a Trip.
 * Holds basic trip information and dynamic financial totals fetched from Firebase.
 */
public class TripModel {

    // --- Original Core Fields ---
    private final String tripId;
    private final String tripName;
    private final String destination;
    private final String membersListString;
    private final int memberCount;
    private final String startDate;
    private final String endDate;

    // --- State & Financial Fields ---
    private int isPinnedState = 0; // 0 = unpinned, 1 = pinned

    // Financial fields (populated dynamically after fetching from Firebase collections)
    private double totalExpenses = 0.0;
    private double totalPayments = 0.0;
    private double fundBalance = 0.0;

    /**
     * Constructor for initializing core trip data.
     */
    public TripModel(String tripId, String tripName, String destination, String membersListString, int memberCount, String startDate, String endDate) {
        this.tripId = tripId;
        this.tripName = tripName;
        this.destination = destination;
        this.membersListString = membersListString;
        this.memberCount = memberCount;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // --- Getters for Core Fields ---
    public String getTripId() { return tripId; }
    public String getTripName() { return tripName; }
    public String getDestination() { return destination; }
    public String getMembersListString() { return membersListString; }
    public int getMemberCount() { return memberCount; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }

    // --- Pinning Logic ---
    public int getIsPinnedState() { return isPinnedState; }
    public void setIsPinnedState(int isPinnedState) { this.isPinnedState = isPinnedState; }

    // --- Financial Setters & Getters (Used by Async Firebase calls) ---

    public double getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(double totalExpenses) { this.totalExpenses = totalExpenses; }

    public double getTotalPayments() { return totalPayments; }
    public void setTotalPayments(double totalPayments) { this.totalPayments = totalPayments; }

    public double getFundBalance() { return fundBalance; }
    public void setFundBalance(double fundBalance) { this.fundBalance = fundBalance; }
    // Add this variable at the top with the others
    private String inactiveMembers;

    // Add these methods
    public String getInactiveMembers() {
        return inactiveMembers;
    }

    public void setInactiveMembers(String inactiveMembers) {
        this.inactiveMembers = inactiveMembers;
    }
}