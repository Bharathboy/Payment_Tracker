package com.example.paymenttracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver messageReceiver;
    private boolean isReceiverRegistered = false;
    private List<Message> messagesList = new ArrayList<>();
    private MessageAdapter messageAdapter;
    private RecyclerView recyclerViewMessages;
    private TextView statusTextView;
    private ImageButton settingsButton;

    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String WEBHOOK_URL = "webhookUrl";
    public static final String SECRET_KEY = "secretKey";
    public static final String MESSAGES = "messages";

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "SMS Permission Granted!", Toast.LENGTH_SHORT).show();
                    startForwardingService();
                } else {
                    Toast.makeText(this, "SMS Permission Denied. The app cannot function without it.", Toast.LENGTH_LONG).show();
                    statusTextView.setText("SMS permission required");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // ensure layout exists

        // Defensive prefs read (in case older formats exist)
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        try {
            sharedPreferences.getString(MESSAGES, "");
            Log.d("MainActivity", "Reading from new data format. No action needed.");
        } catch (ClassCastException e) {
            sharedPreferences.edit().remove(MESSAGES).apply();
            Log.d("MainActivity", "Detected old data format and cleared messages from SharedPreferences.");
        }

        settingsButton = findViewById(R.id.settingsButton);
        statusTextView = findViewById(R.id.statusTextView);
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));

        loadMessagesFromPrefs();
        messageAdapter = new MessageAdapter(messagesList);
        recyclerViewMessages.setAdapter(messageAdapter);

        checkAndRequestSmsPermission();

        settingsButton.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_webhook_settings, null);
            builder.setView(dialogView);

            final EditText dialogWebhookUrlEditText = dialogView.findViewById(R.id.dialogWebhookUrlEditText);
            final EditText dialogSecretKeyEditText = dialogView.findViewById(R.id.dialogSecretKeyEditText);
            Button dialogSaveButton = dialogView.findViewById(R.id.dialogSaveButton);
            Button dialogTestButton = dialogView.findViewById(R.id.dialogTestButton);
            Button dialogCancelButton = dialogView.findViewById(R.id.dialogCancelButton);

            loadSettingsForDialog(dialogWebhookUrlEditText, dialogSecretKeyEditText);

            final AlertDialog dialog = builder.create();
            Window window = dialog.getWindow();
            if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            dialogCancelButton.setOnClickListener(dv -> dialog.dismiss());
            dialogSaveButton.setOnClickListener(dv_save -> {
                String webhookUrl = dialogWebhookUrlEditText.getText().toString().trim();
                String secretKey = dialogSecretKeyEditText.getText().toString().trim();
                if (webhookUrl.isEmpty() || secretKey.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Webhook URL and Secret Key cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveSettingsFromDialog(webhookUrl, secretKey);
                statusTextView.setText("Settings saved");
                checkAndRequestSmsPermission();
                dialog.dismiss();
            });
            dialogTestButton.setOnClickListener(dv_test -> {
                String webhookUrl = dialogWebhookUrlEditText.getText().toString().trim();
                String secretKey = dialogSecretKeyEditText.getText().toString().trim();
                if (webhookUrl.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Webhook URL cannot be empty for testing", Toast.LENGTH_SHORT).show();
                    return;
                }
                testWebhook(webhookUrl, secretKey);
            });
            dialog.show();
        });
    }

    private void saveSettingsFromDialog(String webhookUrl, String secretKey) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(WEBHOOK_URL, webhookUrl);
        editor.putString(SECRET_KEY, secretKey);
        editor.apply();
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
    }

    private void loadSettingsForDialog(EditText urlEditText, EditText keyEditText) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String webhookUrl = sharedPreferences.getString(WEBHOOK_URL, "");
        String secretKey = sharedPreferences.getString(SECRET_KEY, "");
        urlEditText.setText(webhookUrl);
        keyEditText.setText(secretKey);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMessagesFromPrefs();
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter("com.example.paymenttracker.NEW_MESSAGE");
            messageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("com.example.paymenttracker.NEW_MESSAGE".equals(intent.getAction())) {
                        Log.d("MainActivity", "Broadcast received in MainActivity");

                        Message newMessage = null;
                        try {
                            newMessage = intent.getParcelableExtra("com.example.paymenttracker.MESSAGE_OBJECT");
                        } catch (Exception e) {
                            Log.w("MainActivity", "Failed to get parcelable Message from intent - will use extras fallback.", e);
                        }

                        if (newMessage == null) {
                            String sender = intent.getStringExtra("sender");
                            String body = intent.getStringExtra("body");
                            String status = intent.getStringExtra("status");
                            String timestamp = intent.getStringExtra("timestamp");
                            if (timestamp == null) timestamp = String.valueOf(System.currentTimeMillis());
                            if (body == null) body = "";
                            if (sender == null) sender = "UNKNOWN";
                            if (status == null) status = "UNKNOWN";

                            // Normalize any prior INVALID_FORMAT or INVALID values to IGNORED
                            if ("INVALID_FORMAT".equalsIgnoreCase(status) || "INVALID".equalsIgnoreCase(status)) {
                                status = "IGNORED";
                            }

                            newMessage = new Message(sender, body, status, timestamp);
                            Log.d("MainActivity", "Constructed Message from extras fallback: " + body);
                        }

                        if (newMessage != null) {
                            final Message finalMsg = newMessage;
                            runOnUiThread(() -> {
                                messagesList.add(0, finalMsg);
                                messageAdapter.notifyItemInserted(0);
                                recyclerViewMessages.scrollToPosition(0);
                            });
                            Log.d("MainActivity", "New message added: " + newMessage.content);
                        } else {
                            Log.d("MainActivity", "Received message is null even after fallback!");
                        }
                    }
                }
            };

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(messageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(messageReceiver, filter);
            }
            isReceiverRegistered = true;
            Log.d("MainActivity", "Message receiver registered.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isReceiverRegistered) {
            unregisterReceiver(messageReceiver);
            isReceiverRegistered = false;
        }
    }

    /**
     * loadMessagesFromPrefs supports JSON storage (new) plus migration from old "|||"-delimited format.
     * It also normalizes statuses so any stored INVALID_FORMAT becomes IGNORED.
     */
    private void loadMessagesFromPrefs() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String messagesString = sharedPreferences.getString(MESSAGES, "");
        List<Message> loadedMessages = new ArrayList<>();

        if (messagesString != null && !messagesString.isEmpty()) {
            messagesString = messagesString.trim();
            if (messagesString.startsWith("[")) {
                try {
                    JSONArray arr = new JSONArray(messagesString);
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            JSONObject o = arr.getJSONObject(i);
                            String sender = o.optString("sender", "UNKNOWN");
                            String body = o.optString("body", "");
                            String status = o.optString("status", "UNKNOWN");
                            String timestamp = o.optString("timestamp", String.valueOf(System.currentTimeMillis()));

                            // Normalize legacy statuses
                            if ("INVALID_FORMAT".equalsIgnoreCase(status) || "INVALID".equalsIgnoreCase(status)) {
                                status = "IGNORED";
                            }

                            loadedMessages.add(new Message(sender, body, status, timestamp));
                            Log.d("MainActivity", "Loaded message from JSON: " + body);
                        } catch (JSONException je) {
                            Log.w("MainActivity", "Skipping malformed JSON message entry.", je);
                        }
                    }
                } catch (JSONException e) {
                    Log.w("MainActivity", "Messages string looks like JSON but failed to parse. Clearing it.", e);
                }
            } else {
                // Migration path for old "|||"-delimited format:
                Log.d("MainActivity", "Detected old messages format — migrating to JSON storage.");
                String[] tokens = messagesString.split("\\|\\|\\|");
                for (int i = 0; i + 3 < tokens.length; i += 4) {
                    String sender = tokens[i];
                    String body = tokens[i + 1];
                    String status = tokens[i + 2];
                    String timestamp = tokens[i + 3];

                    // Normalize legacy statuses
                    if ("INVALID_FORMAT".equalsIgnoreCase(status) || "INVALID".equalsIgnoreCase(status)) {
                        status = "IGNORED";
                    }

                    loadedMessages.add(new Message(sender, body, status, timestamp));
                    Log.d("MainActivity", "Migrated message: " + body);
                }

                // Attempt to re-save as JSON so migration runs only once
                try {
                    JSONArray newArr = new JSONArray();
                    for (Message m : loadedMessages) {
                        JSONObject o = new JSONObject();
                        o.put("sender", m.sender != null ? m.sender : "UNKNOWN");
                        o.put("body", m.content != null ? m.content : "");
                        o.put("status", m.status != null ? m.status : "UNKNOWN");
                        o.put("timestamp", m.timestamp != null ? m.timestamp : String.valueOf(System.currentTimeMillis()));
                        newArr.put(o);
                    }
                    sharedPreferences.edit().putString(MESSAGES, newArr.toString()).apply();
                    Log.d("MainActivity", "Migration complete — saved messages back as JSON.");
                } catch (JSONException e) {
                    Log.e("MainActivity", "Failed to migrate old messages format.", e);
                }
            }
        }

        Collections.sort(loadedMessages, new Comparator<Message>() {
            @Override
            public int compare(Message m1, Message m2) {
                long t1 = 0, t2 = 0;
                try { t1 = Long.parseLong(m1.timestamp); } catch (Exception ignored) {}
                try { t2 = Long.parseLong(m2.timestamp); } catch (Exception ignored) {}
                return Long.compare(t2, t1);
            }
        });

        messagesList.clear();
        messagesList.addAll(loadedMessages);
        if (messageAdapter != null) messageAdapter.notifyDataSetChanged();
        Log.d("MainActivity", "Loaded " + messagesList.size() + " messages from prefs and sorted them.");
    }

    public void checkAndRequestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            startForwardingService();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS);
        }
    }

    public void startForwardingService() {
        Intent serviceIntent = new Intent(this, SmsForwardingService.class);
        startForegroundService(serviceIntent);
        statusTextView.setText("Service running");
        Log.d("MainActivity", "Foreground service started successfully.");
    }

    private void testWebhook(String webhookUrl, String secretKey) {
        // Simple test payload executed on a background thread
        Thread t = new Thread(() -> {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            org.json.JSONObject jsonPayload = new org.json.JSONObject();
            try {
                jsonPayload.put("amount_received", "1.00");
                jsonPayload.put("upi_ref_id", "TEST1234567890");
                jsonPayload.put("sender_name", "Test User");
                jsonPayload.put("sender_vpa", "test@upi");
                jsonPayload.put("full_sms_body", "This is a test message from your app.");
            } catch (org.json.JSONException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error creating test payload", Toast.LENGTH_SHORT).show());
                return;
            }
            okhttp3.MediaType JSON = okhttp3.MediaType.get("application/json; charset=utf-8");
            okhttp3.RequestBody body = okhttp3.RequestBody.create(jsonPayload.toString(), JSON);
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("X-My-App-Signature", secretKey == null ? "" : secretKey)
                    .build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                final boolean ok = response.isSuccessful();
                final int code = response.code();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, ok ? "Test Webhook OK: " + code : "Test Webhook Failed: " + code, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Test webhook error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
        t.start();
    }
}

