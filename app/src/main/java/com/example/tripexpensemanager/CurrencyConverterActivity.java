package com.example.tripexpensemanager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.view.GravityCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CurrencyConverterActivity extends BaseDrawerActivity {

    private static final String TAG = "CurrencyConverter";

    private EditText editAmount;
    private Spinner spinnerFrom, spinnerTo;
    private TextView txtResult, txtRateInfo;

    private ArrayAdapter<String> adapterFrom;
    private ArrayAdapter<String> adapterTo;

    // Dynamic list that will store all formatted names: "INR (India)", "USD (USA)"
    private final List<String> currencyList = new ArrayList<>();

    // Custom map for popular countries
    private final Map<String, String> popularCountries = new HashMap<>();

    // Background thread manager
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Cached JSON data
    private JSONObject cachedRates = null;
    private String currentBaseCode = "";
    private String currentIstTime = "";
    private boolean isInitialLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_currency_converter);

        setupUniversalDrawer(R.id.drawer_layout, R.id.navigation_view);

        ImageButton btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        if (btnOpenDrawer != null) {
            btnOpenDrawer.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        setupBottomNavigation();
        setupPopularCountries();

        // Bind UI Elements
        editAmount = findViewById(R.id.edit_amount);
        spinnerFrom = findViewById(R.id.spinner_from);
        spinnerTo = findViewById(R.id.spinner_to);
        txtResult = findViewById(R.id.txt_conversion_result);
        txtRateInfo = findViewById(R.id.txt_exchange_rate_info);
        ImageView btnSwap = findViewById(R.id.btn_swap);

        // Populate default starter list before network call returns
        populateDefaultCurrencies();

        // Spinners Setup
        adapterFrom = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencyList);
        adapterFrom.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrom.setAdapter(adapterFrom);

        adapterTo = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencyList);
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTo.setAdapter(adapterTo);

        // Default selections
        setSpinnerSelection(spinnerFrom, "INR");
        setSpinnerSelection(spinnerTo, "USD");

        // 1. Swap Button
        btnSwap.setOnClickListener(v -> swapCurrencies());

        // 2. Real-Time Typing Listener
        editAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                calculateAndDisplay();
            }
        });

        // 3. Spinner Selection Listener
        AdapterView.OnItemSelectedListener spinnerListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isInitialLoad) return;

                // Extract only the 3-letter code for the API (e.g., "INR" from "INR (India)")
                String selectedString = spinnerFrom.getSelectedItem() != null ? spinnerFrom.getSelectedItem().toString() : "INR";
                String selectedCode = selectedString.substring(0, 3);

                if (!selectedCode.equals(currentBaseCode)) {
                    fetchLiveRates(selectedCode);
                } else {
                    calculateAndDisplay();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerFrom.setOnItemSelectedListener(spinnerListener);
        spinnerTo.setOnItemSelectedListener(spinnerListener);

        // Initial fetch on activity launch
        fetchLiveRates("INR");
    }

    // Set up the custom mapping for exactly how you want top countries to look
    private void setupPopularCountries() {
        popularCountries.put("INR", "India");
        popularCountries.put("USD", "USA");
        popularCountries.put("EUR", "Eurozone");
        popularCountries.put("GBP", "UK");
        popularCountries.put("AUD", "Australia");
        popularCountries.put("CAD", "Canada");
        popularCountries.put("SGD", "Singapore");
        popularCountries.put("JPY", "Japan");
        popularCountries.put("CNY", "China");
        popularCountries.put("AED", "UAE");
        popularCountries.put("CHF", "Switzerland");
        popularCountries.put("NZD", "New Zealand");
        popularCountries.put("ZAR", "South Africa");
    }

    // Helper to format currency codes beautifully
    private String getFormattedCurrencyName(String code) {
        // 1. Check if it's in our custom country list first
        if (popularCountries.containsKey(code)) {
            return code + " (" + popularCountries.get(code) + ")";
        }
        // 2. If not, use Android's built-in currency database for the rest of the world
        try {
            String name = java.util.Currency.getInstance(code).getDisplayName();
            return code + " (" + name + ")";
        } catch (Exception e) {
            return code; // Safe fallback if Android doesn't recognize it
        }
    }

    private void populateDefaultCurrencies() {
        String[] defaults = {"INR", "USD", "EUR", "GBP", "AUD", "CAD", "SGD", "JPY", "CNY", "AED", "CHF", "NZD", "ZAR"};
        for (String code : defaults) {
            currencyList.add(getFormattedCurrencyName(code));
        }
    }

    private void swapCurrencies() {
        int fromPos = spinnerFrom.getSelectedItemPosition();
        int toPos = spinnerTo.getSelectedItemPosition();
        spinnerFrom.setSelection(toPos);
        spinnerTo.setSelection(fromPos);
    }

    private void fetchLiveRates(String baseCode) {
        txtRateInfo.setText("Fetching All Live Rates...");

        executor.execute(() -> {
            try {
                URL url = new URL("https://open.er-api.com/v6/latest/" + baseCode);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject jsonObject = new JSONObject(response.toString());
                    cachedRates = jsonObject.getJSONObject("rates");
                    currentBaseCode = baseCode;

                    // Extract and format ALL available currencies
                    List<String> formattedApiCurrencies = new ArrayList<>();
                    Iterator<String> keys = cachedRates.keys();
                    while (keys.hasNext()) {
                        formattedApiCurrencies.add(getFormattedCurrencyName(keys.next()));
                    }
                    Collections.sort(formattedApiCurrencies);

                    // Parse UTC Unix Time to IST
                    long unixTime = jsonObject.optLong("time_last_update_unix", 0);
                    if (unixTime > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);
                        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                        currentIstTime = sdf.format(new Date(unixTime * 1000));
                    } else {
                        currentIstTime = "Recently";
                    }

                    // Update UI on Main Thread
                    handler.post(() -> {
                        updateCurrencyList(formattedApiCurrencies);
                        calculateAndDisplay();
                        isInitialLoad = false;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching live rates", e);
                handler.post(() -> txtRateInfo.setText("Network Error. Showing offline rates."));
            }
        });
    }

    private void updateCurrencyList(List<String> newCurrencies) {
        if (newCurrencies.isEmpty()) return;

        // Remember the 3-letter codes we had selected
        String selectedFromCode = spinnerFrom.getSelectedItem() != null ? spinnerFrom.getSelectedItem().toString().substring(0, 3) : "INR";
        String selectedToCode = spinnerTo.getSelectedItem() != null ? spinnerTo.getSelectedItem().toString().substring(0, 3) : "USD";

        currencyList.clear();
        currencyList.addAll(newCurrencies);

        adapterFrom.notifyDataSetChanged();
        adapterTo.notifyDataSetChanged();

        // Restore selections using the 3-letter codes
        setSpinnerSelection(spinnerFrom, selectedFromCode);
        setSpinnerSelection(spinnerTo, selectedToCode);
    }

    private void setSpinnerSelection(Spinner spinner, String targetCode) {
        for (int i = 0; i < currencyList.size(); i++) {
            if (currencyList.get(i).startsWith(targetCode)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void calculateAndDisplay() {
        String amountStr = editAmount.getText().toString().trim();

        if (spinnerFrom.getSelectedItem() == null || spinnerTo.getSelectedItem() == null) return;

        // Safely extract just the 3-letter codes for our math and JSON logic!
        String fromCode = spinnerFrom.getSelectedItem().toString().substring(0, 3);
        String toCode = spinnerTo.getSelectedItem().toString().substring(0, 3);

        double amount = amountStr.isEmpty() ? 0.0 : Double.parseDouble(amountStr);

        if (fromCode.equals(toCode)) {
            txtResult.setText(String.format(Locale.US, "%.2f %s", amount, toCode));
            txtRateInfo.setText(String.format(Locale.US, "1 %s = 1 %s", fromCode, toCode));
            return;
        }

        if (cachedRates == null) {
            txtResult.setText("0.00");
            return;
        }

        double exchangeRate = cachedRates.optDouble(toCode, 0.0);
        double finalAmount = amount * exchangeRate;

        txtResult.setText(String.format(Locale.US, "%.2f %s", finalAmount, toCode));
        txtRateInfo.setText(String.format(Locale.US, "Live Rate: 1 %s = %.4f %s\nUpdated: %s (IST)",
                fromCode, exchangeRate, toCode, currentIstTime));
    }

    private void setupBottomNavigation() {
        findViewById(R.id.btn_home_utility).setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_view_trips_utility).setOnClickListener(v -> {
            Intent intent = new Intent(this, TripListActivity.class);
            intent.putExtra("CATEGORY_TITLE", "All Trips");
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_dash_utility).setOnClickListener(v -> startActivity(new Intent(this, UtilityActivity.class)));

        findViewById(R.id.btn_settings_utility).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}