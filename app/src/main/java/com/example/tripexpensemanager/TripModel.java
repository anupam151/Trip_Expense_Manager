package com.example.tripexpensemanager;

import java.util.ArrayList;

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

    // --- NEW RBAC (Role-Based Access) Variables ---
    private ArrayList<TripMember> memberDetails = new ArrayList<>();
    private ArrayList<String> sharedEmails = new ArrayList<>();
    private String ownerEmail = ""; // Tracks the Admin

    // --- State & Financial Fields ---
    private int isPinnedState = 0; // 0 = unpinned, 1 = pinned

    // Financial fields (populated dynamically after fetching from Firebase collections)
    private double totalExpenses = 0.0;
    private double totalPayments = 0.0;
    private double fundBalance = 0.0;

    private String inactiveMembers;

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

    public String getInactiveMembers() { return inactiveMembers; }
    public void setInactiveMembers(String inactiveMembers) { this.inactiveMembers = inactiveMembers; }

    // ==========================================
    // --- NEW RBAC Getters and Setters ---
    // ==========================================
    @SuppressWarnings("unused")
    public ArrayList<TripMember> getMemberDetails() {
        return memberDetails;
    }

    public void setMemberDetails(ArrayList<TripMember> memberDetails) {
        this.memberDetails = memberDetails;
    }
    @SuppressWarnings("unused")
    public ArrayList<String> getSharedEmails() {
        return sharedEmails;
    }
    @SuppressWarnings("unused")
    public void setSharedEmails(ArrayList<String> sharedEmails) {
        this.sharedEmails = sharedEmails;
    }
    @SuppressWarnings("unused")
    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    // --- NEW: Calculates the role dynamically for Click Blockers ---
    public String getCurrentUserRole(String userEmail) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            return "Viewer";
        }

        // 1. Is this the creator of the trip?
        if (userEmail.equalsIgnoreCase(ownerEmail)) {
            return "Admin";
        }

        // 2. Check the member list for an assigned role
        if (memberDetails != null) {
            for (TripMember m : memberDetails) {
                if (m.getEmailId() != null && m.getEmailId().equalsIgnoreCase(userEmail)) {
                    return m.getRole() != null ? m.getRole() : "Viewer";
                }
            }
        }

        // Default to Viewer if they somehow access it without an explicit role
        return "Viewer";
    }
}