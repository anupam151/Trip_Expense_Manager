package com.example.tripexpensemanager;

import android.app.Application;
//import androidx.appcompat.app.AppCompatDelegate;

public class TripExpenseApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // This single line forces the entire app to strictly run in Light Mode
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }
}