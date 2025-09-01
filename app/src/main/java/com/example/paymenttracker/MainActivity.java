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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
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
                    statusTextView.setText(getString(R.string.status_sms_permission_required));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            dialogCancelButton.setOnClickListener(dv -> dialog.dismiss());
            dialogSaveButton.setOnClickListener(dv_save -> {
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
                        Message newMessage = intent.getParcelableExtra("com.example.paymenttracker.MESSAGE_OBJECT");
                        if (newMessage != null) {
                            runOnUiThread(() -> {
                                messagesList.add(0, newMessage);
                                messageAdapter.notifyItemInserted(0);
                                recyclerViewMessages.scrollToPosition(0);
                            });
                            Log.d("MainActivity", "New message added: " + newMessage.content);
                        } else {
                            Log.d("MainActivity", "Received message is null!");
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

    private void loadMessagesFromPrefs() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String messagesString = sharedPreferences.getString(MESSAGES, "");
        List<Message> loadedMessages = new ArrayList<>();
        if (!messagesString.isEmpty()) {
            String[] messageStrings = messagesString.split("\\|\\|\\|");
            for (String messageString : messageStrings) {
                loadedMessages.add(Message.fromString(messageString));
            }
        }

        Collections.sort(loadedMessages, new Comparator<Message>() {
            @Override
            public int compare(Message m1, Message m2) {
                try {
                    long t1 = Long.parseLong(m1.timestamp);
                    long t2 = Long.parseLong(m2.timestamp);
                    return Long.compare(t2, t1);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        });

        messagesList.clear();
        messagesList.addAll(loadedMessages);
        if (messageAdapter != null) {
            messageAdapter.notifyDataSetChanged();
        }
        Log.d("MainActivity", "Loaded " + messagesList.size() + " messages from prefs.");
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

    private void testWebhook(String webhookUrl, String secretKey) {
        JSONObject jsonPayload = new JSONObject();
        try {
            jsonPayload.put("amount_received", "1.00");
            jsonPayload.put("upi_ref_id", "TEST1234567890");
            jsonPayload.put("sender_name", "Test User");
            jsonPayload.put("sender_vpa", "test@upi");
            jsonPayload.put("full_sms_body", "This is a test message from your app.");
        } catch (JSONException e) {
            mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error creating test payload", Toast.LENGTH_SHORT).show());
            return;
        }

        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonPayload.toString(), JSON);

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .addHeader("X-My-App-Signature", secretKey)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Test Webhook Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e("MainActivity", "Test webhook failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Test Webhook Successful! Response: " + response.code(), Toast.LENGTH_LONG).show());
                } else {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Test Webhook Failed with Code: " + response.code(), Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }
}