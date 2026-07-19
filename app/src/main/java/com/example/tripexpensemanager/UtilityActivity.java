package com.example.tripexpensemanager;

import android.os.Bundle;
import android.widget.ImageButton;
import androidx.appcompat.app.AlertDialog;


import androidx.core.view.GravityCompat;

// 1. Extend BaseDrawerActivity instead of Activity
public class UtilityActivity extends BaseDrawerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_utility);

        // 2. Wire up the Universal Drawer
        // Note: Ensure the NavigationView inside your layout_universal_drawer.xml has android:id="@+id/nav_view"
        setupUniversalDrawer(R.id.drawer_layout, R.id.navigation_view);

        // 3. Make the custom hamburger menu button open the drawer
        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // 1. Bill Splitter Button
        androidx.cardview.widget.CardView cardToolBillSplitter = findViewById(R.id.card_tool_bill_splitter);
        if (cardToolBillSplitter != null) {
            cardToolBillSplitter.setOnClickListener(v ->
                    new AlertDialog.Builder(UtilityActivity.this)
                            .setTitle("Work in Progress")
                            .setMessage("The Quick Bill Splitter feature is currently under development. Please check back later!")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show()
            );
        }

        //2. All Time Statistics
        androidx.cardview.widget.CardView cardToolStatistics = findViewById(R.id.card_tool_statistics);
        if (cardToolStatistics != null) {
            cardToolStatistics.setOnClickListener(v ->
                    new AlertDialog.Builder(UtilityActivity.this)
                            .setTitle("Work in Progress")
                            .setMessage("The All-Time Statistics feature is currently under development. Please check back later!")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show()
            );
        }


        //3. Report Vault
        androidx.cardview.widget.CardView cardToolReportsVault = findViewById(R.id.card_tool_reports_vault);
        if (cardToolReportsVault != null) {
            cardToolReportsVault.setOnClickListener(v ->
                    new AlertDialog.Builder(UtilityActivity.this)
                            .setTitle("Work in Progress")
                            .setMessage("The Reports Vault feature is currently under development. Please check back later!")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show()
            );
        }


        //4. Live Currency Converter
        androidx.cardview.widget.CardView cardToolCurrency = findViewById(R.id.card_tool_currency);
        if (cardToolCurrency != null) {
            cardToolCurrency.setOnClickListener(v ->
                    new AlertDialog.Builder(UtilityActivity.this)
                            .setTitle("Work in Progress")
                            .setMessage("The Live Currency Converter feature is currently under development. Please check back later!")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show()
            );
        }


        //5. Travel Checklist
        androidx.cardview.widget.CardView cardToolChecklist = findViewById(R.id.card_tool_checklist);
        if (cardToolChecklist != null) {
            cardToolChecklist.setOnClickListener(v ->
                    new AlertDialog.Builder(UtilityActivity.this)
                            .setTitle("Work in Progress")
                            .setMessage("The Travel Checklist feature is currently under development. Please check back later!")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show()
            );
        }


        //6. Road Trip Fuel Calculator
        androidx.cardview.widget.CardView cardToolFuelCalc = findViewById(R.id.card_tool_fuel);
        if (cardToolFuelCalc != null) {
            cardToolFuelCalc.setOnClickListener(v ->
                    new AlertDialog.Builder(UtilityActivity.this)
                            .setTitle("Work in Progress")
                            .setMessage("The Road Trip Fuel Calculator feature is currently under development. Please check back later!")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show()
            );
        }


        //7. Archived Trips
        androidx.cardview.widget.CardView cardToolArchived = findViewById(R.id.card_tool_archived);
        if (cardToolArchived != null) {
            cardToolArchived.setOnClickListener(v ->
                    new AlertDialog.Builder(UtilityActivity.this)
                            .setTitle("Work in Progress")
                            .setMessage("The Archived Trips feature is currently under development. Please check back later!")
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show()
            );
        }



    }
}