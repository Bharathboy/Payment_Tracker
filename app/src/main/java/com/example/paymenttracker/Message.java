package com.example.paymenttracker;

import android.Manifest;
import android.content.BroadcastReceiver; // Added
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter; // Added
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList; // Added
import java.util.List; // Added

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {

    private TextView statusTextView;
    private View rootLayout;
    private Button settingsButton;
    private RecyclerView recyclerViewMessages; // Made a field
    private MessageAdapter messageAdapter; // Made a field
    private List<Message> messagesList = new ArrayList<>(); // Field for messages

    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String WEBHOOK_URL = "webhookUrl";
    public static final String SECRET_KEY = "secretKey";

    // Constants for BroadcastReceiver
    public static final String ACTION_NEW_MESSAGE = "com.example.paymenttracker.NEW_MESSAGE";
    public static final String EXTRA_NEW_MESSAGE = "com.example.paymenttracker.MESSAGE_OBJECT";

    private NewMessageReceiver newMessageReceiver;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "SMS Permission Granted!", Toast.LENGTH_SHORT).show();
                    startForwardingService();
                } else {
                    Toast.makeText(this, "SMS Permission Denied. The app cannot function without it.", Toast.LENGTH_LONG).show();
                    statusTextView.setText(getString(R.string.status_sms_permission_required));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingsButton = findViewById(R.id.settingsButton);
        statusTextView = findViewById(R.id.statusTextView);
        rootLayout = findViewById(R.id.root_layout);
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages); // Initialize field

        messagesList.addAll(getSampleMessages()); // Populate with initial sample messages
        messageAdapter = new MessageAdapter(messagesList); // Initialize field
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMessages.setAdapter(messageAdapter);

        newMessageReceiver = new NewMessageReceiver(); // Instantiate receiver

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

            dialogSaveButton.setOnClickListener(dv -> {
                String webhookUrl = dialogWebhookUrlEditText.getText().toString().trim();
                String secretKey = dialogSecretKeyEditText.getText().toString().trim();

                if (webhookUrl.isEmpty() || secretKey.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Webhook URL and Secret Key cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveSettingsFromDialog(webhookUrl, secretKey);
                statusTextView.setText(R.string.status_ready_save_settings);
                checkAndRequestSmsPermission();
                dialog.dismiss();
            });

            dialogTestButton.setOnClickListener(dv -> {
                String webhookUrl = dialogWebhookUrlEditText.getText().toString().trim();
                String secretKey = dialogSecretKeyEditText.getText().toString().trim();

                if (webhookUrl.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Webhook URL cannot be empty for testing", Toast.LENGTH_SHORT).show();
                    return;
                }
                JSONObject jsonPayload = new JSONObject();
                try {
                    jsonPayload.put("message", "This is a test message from Payment Tracker App");
                    jsonPayload.put("sender", "SYSTEM_TEST");
                    jsonPayload.put("timestamp", System.currentTimeMillis());
                } catch (JSONException e) {
                    Log.e("MainActivity", "Error creating test JSON", e);
                    Toast.makeText(MainActivity.this, "Error creating test JSON", Toast.LENGTH_SHORT).show();
                    return;
                }
                performTestWebhookRequest(webhookUrl, secretKey, jsonPayload.toString());
            });

            dialogCancelButton.setOnClickListener(dv -> dialog.dismiss());
            dialog.show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_NEW_MESSAGE);
        // Consider using ContextCompat.registerReceiver() if targeting Android Tiramisu (API 33) or higher for exportability
        registerReceiver(newMessageReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(newMessageReceiver);
    }

    private void loadSettingsForDialog(EditText urlEditText, EditText keyEditText) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String webhookUrl = sharedPreferences.getString(WEBHOOK_URL, "");
        String secretKey = sharedPreferences.getString(SECRET_KEY, "");
        urlEditText.setText(webhookUrl);
        keyEditText.setText(secretKey);
    }

    private void saveSettingsFromDialog(String webhookUrl, String secretKey) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(WEBHOOK_URL, webhookUrl);
        editor.putString(SECRET_KEY, secretKey);
        editor.apply();
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
    }

    private List<Message> getSampleMessages() { // This method now just returns a list, not modifies a field
        List<Message> samples = new ArrayList<>();
        samples.add(new androidx.datastore.core.Message(
                "+919741430392",
                "Your a/c is credited with Rs10.99 on 31-08-2025 from Test User with VPA test@upi UPI Ref No 123456789012",
                "IGNORED",
                "Aug 31, 2025 22:28"
        ));
        samples.add(new androidx.datastore.core.Message(
                "JX-KOTAKB-S",
                "Sent Rs.10.37 from Kotak Bank AC X2052 to riseupbab@ybl on 31-08-25.UPI Ref 136056932435. Not you, https://kotak.com/KBANKT/Fraud",
                "IGNORED",
                "Aug 31, 2025 19:31"
        ));
        samples.add(new androidx.datastore.core.Message(
                "JX-KOTAKB-S",
                "Received Rs.10.37 in your Kotak Bank AC X2052 from bharath.0515-3@waaxis on 31-08-25.UPI Ref:136056932435.",
                "SUBMITTED",
                "Aug 31, 2025 19:31"
        ));
        // Add other sample messages if needed
        return samples;
    }

    private void performTestWebhookRequest(String url, String key, String jsonPayload) {
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request.Builder requestBuilder = new Request.Builder().url(url).post(body);
        if (key != null && !key.isEmpty()) {
            requestBuilder.addHeader("X-Secret-Key", key);
        }
        Request request = requestBuilder.build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Log.e("MainActivity", "Test webhook failed: " + e.getMessage(), e);
                    Toast.makeText(MainActivity.this, "Test failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBodyString = response.body() != null ? response.body().string() : "Empty response";
                final boolean isSuccessful = response.isSuccessful();
                final int responseCode = response.code();
                runOnUiThread(() -> {
                    if (isSuccessful) {
                        Log.i("MainActivity", "Test webhook successful. Response: " + responseBodyString);
                        Toast.makeText(MainActivity.this, "Test successful!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w("MainActivity", "Test webhook not successful. Code: " + responseCode + ", Body: " + responseBodyString);
                        Toast.makeText(MainActivity.this, "Test failed: " + responseCode, Toast.LENGTH_LONG).show();
                    }
                });
                if (response.body() != null) { response.body().close(); }
            }
        });
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
        statusTextView.setText(getString(R.string.status_service_running));
        Log.d("MainActivity", "Foreground service started successfully.");
    }

    // Inner BroadcastReceiver class
    private class NewMessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_NEW_MESSAGE.equals(intent.getAction())) {
                Message newMessage = intent.getParcelableExtra(EXTRA_NEW_MESSAGE);
                if (newMessage != null && messageAdapter != null) {
                    messagesList.add(0, newMessage); // Add to the beginning of the list
                    messageAdapter.notifyItemInserted(0);
                    recyclerViewMessages.scrollToPosition(0); // Scroll to show the new message
                }
            }
        }
    }
}
