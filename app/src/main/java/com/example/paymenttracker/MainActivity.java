package com.example.paymenttracker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import jp.wasabeef.blurry.Blurry;
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
    private Button testButton;
    private TextView statusTextView;
    private ImageView blurBackground;
    private View rootLayout;
    private RecyclerView recyclerViewMessages;
    private MessageAdapter messageAdapter;


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

        }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link our Java variables to the UI elements in the XML layout
    webhookUrlEditText = findViewById(R.id.webhookUrlEditText);
    secretKeyEditText = findViewById(R.id.secretKeyEditText);
    saveButton = findViewById(R.id.saveButton);
    testButton = findViewById(R.id.testButton);
    statusTextView = findViewById(R.id.statusTextView);
    blurBackground = findViewById(R.id.blur_background);
    rootLayout = findViewById(R.id.root_layout);
    recyclerViewMessages = findViewById(R.id.recyclerViewMessages);

    // Setup RecyclerView for messages
    recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
    messageAdapter = new MessageAdapter(getSampleMessages());
    recyclerViewMessages.setAdapter(messageAdapter);
    // Sample data for demonstration
    private java.util.List<Message> getSampleMessages() {
    java.util.List<Message> messages = new java.util.ArrayList<>();
    messages.add(new Message(
        "+919741430392",
        "Your a/c is credited with Rs10.99 on 31-08-2025 from Test User with VPA test@upi UPI Ref No 123456789012",
        "IGNORED",
        "Aug 31, 2025 22:28"
    ));
    messages.add(new Message(
        "JX-KOTAKB-S",
        "Sent Rs.10.37 from Kotak Bank AC X2052 to riseupbab@ybl on 31-08-25.UPI Ref 136056932435. Not you, https://kotak.com/KBANKT/Fraud",
        "IGNORED",
        "Aug 31, 2025 19:31"
    ));
    messages.add(new Message(
        "JX-KOTAKB-S",
        "Received Rs.10.37 in your Kotak Bank AC X2052 from bharath.0515-3@waaxis on 31-08-25.UPI Ref:136056932435.",
        "SUBMITTED",
        "Aug 31, 2025 19:31"
    ));
    messages.add(new Message(
        "JK-KOTAKB-S",
        "Sent Rs.1.00 from Kotak Bank AC X2052 to riseupbab@ybl on 31-08-25.UPI Ref 133192562435. Not you, https://kotak.com/KBANKT/Fraud",
        "IGNORED",
        "Aug 31, 2025 19:01"
    ));
    messages.add(new Message(
        "JD-KOTAKB-S",
        "Received Rs.1.00 in your Kotak Bank AC X2052 from bharath.0515-3@waaxis on 31-08-25.UPI Ref: 133192562435.",
        "IGNORED",
        "Aug 31, 2025 19:01"
    ));
    return messages;
    }

        // This is the key part: we wait for the layout to be drawn,
        // then we capture it, blur it, and set it as the background.
        rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove the listener to prevent it from running multiple times
                rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Use the Blurry library to create the effect
                Blurry.with(MainActivity.this)
                        .radius(25) // Blur radius
                        .sampling(2) // Downscale factor for performance
                        .capture(rootLayout) // Capture the entire root layout
                        .into(blurBackground); // Apply the blurred image to our ImageView
            }
        });


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