package com.example.tripexpensemanager;

public class TripMember {
    private String memberName;
    private String emailId; // Can be null or empty
    private String role;    // "Admin", "Editor", "Viewer", or null

    // 1. Firebase REQUIRES an empty constructor to pull data from the cloud!
    @SuppressWarnings("unused")
    public TripMember() {
    }

    // 2. Your standard constructor
    public TripMember(String memberName, String emailId, String role) {
        this.memberName = memberName;
        this.emailId = emailId;
        this.role = role;
    }

    // --- Getters and Setters ---
    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}