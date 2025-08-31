// app/src/main/java/com/example/paymenttracker/MainActivity.java

package com.example.paymenttracker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {

    private EditText webhookUrlEditText;
    private EditText secretKeyEditText;
    private Button saveButton;
    private Button testButton; // New test button
    private TextView statusTextView;

    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String WEBHOOK_URL = "webhookUrl";
    public static final String SECRET_KEY = "secretKey";

    // This is the modern, recommended way to handle permission requests.
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "SMS Permission Granted!", Toast.LENGTH_SHORT).show();
                    startForwardingService(); // Start the service now that we have permission
                } else {
                    Toast.makeText(this, "SMS Permission Denied. The app cannot function without it.", Toast.LENGTH_LONG).show();
                    statusTextView.setText("Status: SMS Permission is required.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link our Java variables to the UI elements in the XML layout
        webhookUrlEditText = findViewById(R.id.webhookUrlEditText);
        secretKeyEditText = findViewById(R.id.secretKeyEditText);
        saveButton = findViewById(R.id.saveButton);
        testButton = findViewById(R.id.testButton); // Link to the new test button
        statusTextView = findViewById(R.id.statusTextView);

        loadSettings();

        saveButton.setOnClickListener(v -> saveSettingsAndStartService());
        testButton.setOnClickListener(v -> sendTestWebhook());
    }

    private void saveSettingsAndStartService() {
        // First, save the user's input
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(WEBHOOK_URL, webhookUrlEditText.getText().toString().trim());
        editor.putString(SECRET_KEY, secretKeyEditText.getText().toString().trim());
        editor.apply();
        Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show();

        // Then, check for permission and start the background service
        checkAndRequestSmsPermission();
    }

    private void checkAndRequestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            // Permission is already granted, so we can start the service
            startForwardingService();
        } else {
            // Permission is not granted, so we ask the user for it
            requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS);
        }
    }

    private void startForwardingService() {
        Intent serviceIntent = new Intent(this, SmsForwardingService.class);
        // This command starts the service and makes the notification appear
        startForegroundService(serviceIntent);
        statusTextView.setText("Status: Service is running. Listening for SMS.");
        Log.d("MainActivity", "Foreground service started successfully.");
    }

    private void loadSettings() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String url = sharedPreferences.getString(WEBHOOK_URL, "");
        String secret = sharedPreferences.getString(SECRET_KEY, "");
        webhookUrlEditText.setText(url);
        secretKeyEditText.setText(secret);

        // Check permission on load to update the status text correctly
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            statusTextView.setText("Status: SMS permission needed.");
        } else if (url.isEmpty()) {
            statusTextView.setText("Status: Ready. Save settings to start the service.");
        } else {
            statusTextView.setText("Status: Service is running. Listening for SMS.");
        }
    }

    private void sendTestWebhook() {
        String webhookUrl = webhookUrlEditText.getText().toString().trim();
        String secretKey = secretKeyEditText.getText().toString().trim();

        if (webhookUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a Webhook URL first.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject testPayload = new JSONObject();
        try {
            testPayload.put("message", "This is a test webhook from your Smart SMS Forwarder app!");
            testPayload.put("status", "SUCCESS");
        } catch (JSONException e) {
            // This should not happen
        }

        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(testPayload.toString(), JSON);
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .addHeader("X-My-App-Signature", secretKey)
                .build();

        Toast.makeText(this, "Sending test webhook...", Toast.LENGTH_SHORT).show();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // We must use runOnUiThread to show a Toast from a background thread
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Test Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Test Response Code: " + response.code(), Toast.LENGTH_LONG).show());
                response.close();
            }
        });
    }
}