package com.example.tripexpensemanager;

public class TripModel {
    private final String tripId;
    private final String tripName;
    private final String destination;
    private final String membersListString;
    private final int memberCount;
    private final String startDate;
    private final String endDate;
    private int isPinnedState = 0;

    public TripModel(String tripId, String tripName, String destination, String membersListString, int memberCount, String startDate, String endDate) {
        this.tripId = tripId;
        this.tripName = tripName;
        this.destination = destination;
        this.membersListString = membersListString;
        this.memberCount = memberCount;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getTripId() { return tripId; }
    public String getTripName() { return tripName; }
    public String getDestination() { return destination; }
    public String getMembersListString() { return membersListString; }
    public int getMemberCount() { return memberCount; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }

    public int getIsPinnedState() { return isPinnedState; }
    public void setIsPinnedState(int isPinnedState) { this.isPinnedState = isPinnedState; }
}