// Owned by @bharath_boy on telegram
package com.example.paymenttracker;

// Owned by @bharath_boy on telegram

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements TelegramSender.TelegramSendCallback {
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver messageReceiver;
    private BroadcastReceiver statusReceiver; // New receiver for status updates
    private boolean isReceiverRegistered = false;
    private List<Message> messagesList = new ArrayList<>();
    private MessageAdapter messageAdapter;
    private RecyclerView recyclerViewMessages;
    private TextView statusTextView;
    private TextView emptyStateTextView;
    private ImageButton notificationButton;
    private TelegramSender telegramSender;


    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String WEBHOOK_URL = "webhookUrl";
    public static final String SECRET_KEY = "secretKey";
    public static final String TELEGRAM_BOT_TOKEN = "telegramBotToken";
    public static final String TELEGRAM_CHAT_ID = "telegramChatId";
    public static final String MESSAGES = "messages";

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allPermissionsGranted = true;
                for (Boolean isGranted : permissions.values()) {
                    if (!isGranted) {
                        allPermissionsGranted = false;
                        break;
                    }
                }

                if (allPermissionsGranted) {
                    Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show();
                    startForwardingService();
                } else {
                    Toast.makeText(this, "All required permissions were not granted. App may not function correctly.", Toast.LENGTH_LONG).show();
                    statusTextView.setText("Permissions required");
                    updateStatusIconColor();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        try {
            sharedPreferences.getString(MESSAGES, "");
            Log.d("MainActivity", "Reading from new data format. No action needed.");
        } catch (ClassCastException e) {
            sharedPreferences.edit().remove(MESSAGES).apply();
            Log.d("MainActivity", "Detected old data format and cleared messages from SharedPreferences.");
        }

        ImageButton settingsButton = findViewById(R.id.settingsButton);
        ImageButton menuButton = findViewById(R.id.menuButton);
        notificationButton = findViewById(R.id.notificationButton); // Initialize the new button
        notificationButton.setVisibility(View.GONE); // Hide it by default
        statusTextView = findViewById(R.id.statusTextView);
        ImageView statusInfoIcon = findViewById(R.id.statusInfoIcon);
        statusTextView = findViewById(R.id.statusTextView);
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        emptyStateTextView = findViewById(R.id.emptyStateText);
        telegramSender = new TelegramSender(); // Instantiate TelegramSender


        loadMessagesFromPrefs();
        messageAdapter = new MessageAdapter(messagesList);
        recyclerViewMessages.setAdapter(messageAdapter);

        updateStatusIconColor();
        checkAndRequestPermissions();



        settingsButton.setOnClickListener(v -> showSettingsDialog());
        menuButton.setOnClickListener(v -> showMenuDialog());
        statusInfoIcon.setOnClickListener(v -> showDetailedStatusDialog());

        // The telegramSettingsButton is no longer in the layout, so we don't need to find it here.
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_unified_settings, null);
        builder.setView(dialogView);

        final EditText dialogWebhookUrlEditText = dialogView.findViewById(R.id.dialogWebhookUrlEditText);
        final EditText dialogSecretKeyEditText = dialogView.findViewById(R.id.dialogSecretKeyEditText);
        final EditText dialogTelegramBotTokenEditText = dialogView.findViewById(R.id.dialogTelegramBotTokenEditText);
        final EditText dialogTelegramChatIdEditText = dialogView.findViewById(R.id.dialogTelegramChatIdEditText);

        Button dialogSaveButton = dialogView.findViewById(R.id.dialogSaveButton);
        Button dialogTestWebhookButton = dialogView.findViewById(R.id.dialogTestWebhookButton);
        final Button dialogTestTelegramButton = dialogView.findViewById(R.id.dialogTestTelegramButton); // Add final
        Button dialogCancelButton = dialogView.findViewById(R.id.dialogCancelButton);
        ImageButton webhookInfoButton = dialogView.findViewById(R.id.webhookInfoButton);
        ImageButton telegramInfoButton = dialogView.findViewById(R.id.telegramInfoButton);

        webhookInfoButton.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this, R.style.AlertDialog_App)
                    .setTitle("Webhook Settings Info")
                    .setMessage("A webhook is a way to send real-time data from your app to an external URL. Enter a URL and an optional secret key for security.")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        telegramInfoButton.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this, R.style.AlertDialog_App)
                    .setTitle("Telegram Settings Info")
                    .setMessage("A Telegram bot token and chat ID are required to forward messages to a Telegram chat or channel. You can get these by creating a bot on Telegram.")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        loadSettingsForDialog(dialogWebhookUrlEditText, WEBHOOK_URL);
        loadSettingsForDialog(dialogSecretKeyEditText, SECRET_KEY);
        loadSettingsForDialog(dialogTelegramBotTokenEditText, TELEGRAM_BOT_TOKEN);
        loadSettingsForDialog(dialogTelegramChatIdEditText, TELEGRAM_CHAT_ID);

        final AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        dialogCancelButton.setOnClickListener(dv -> dialog.dismiss());

        dialogSaveButton.setOnClickListener(dv_save -> {
            String webhookUrl = dialogWebhookUrlEditText.getText().toString().trim();
            String secretKey = dialogSecretKeyEditText.getText().toString().trim();
            String botToken = dialogTelegramBotTokenEditText.getText().toString().trim();
            String chatId = dialogTelegramChatIdEditText.getText().toString().trim();

            saveSettingsFromDialog(WEBHOOK_URL, webhookUrl);
            saveSettingsFromDialog(SECRET_KEY, secretKey);
            saveSettingsFromDialog(TELEGRAM_BOT_TOKEN, botToken);
            saveSettingsFromDialog(TELEGRAM_CHAT_ID, chatId);


            Toast.makeText(MainActivity.this, "Settings saved!", Toast.LENGTH_SHORT).show();

            statusTextView.setText("Settings saved");
            updateStatusIconColor();
            checkAndRequestPermissions();
            dialog.dismiss();
        });

        dialogTestWebhookButton.setOnClickListener(dv_test -> {
            String webhookUrl = dialogWebhookUrlEditText.getText().toString().trim();
            String secretKey = dialogSecretKeyEditText.getText().toString().trim();
            if (webhookUrl.isEmpty()) {
                Toast.makeText(MainActivity.this, "Webhook URL cannot be empty for testing", Toast.LENGTH_SHORT).show();
                return;
            }
            testWebhook(webhookUrl, secretKey);
        });

        dialogTestTelegramButton.setOnClickListener(dv_test -> {
            String botToken = dialogTelegramBotTokenEditText.getText().toString().trim();
            String chatId = dialogTelegramChatIdEditText.getText().toString().trim();
            if (botToken.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(MainActivity.this, "Bot token and Chat ID cannot be empty for testing", Toast.LENGTH_SHORT).show();
                return;
            }
            testTelegram(botToken, chatId);
        });

        dialog.show();
    }

    private void showDetailedStatusDialog() {
        // Read current settings and permissions
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String webhookUrl = sharedPreferences.getString(WEBHOOK_URL, "").trim();
        String telegramBotToken = sharedPreferences.getString(TELEGRAM_BOT_TOKEN, "").trim();
        String telegramChatId = sharedPreferences.getString(TELEGRAM_CHAT_ID, "").trim();

        boolean isWebhookConfigured = !webhookUrl.isEmpty();
        boolean isTelegramConfigured = !telegramBotToken.isEmpty() && !telegramChatId.isEmpty();
        boolean isPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean isNotificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        String title = "Detailed App Status";
        StringBuilder messageBuilder = new StringBuilder();

        // Status of permissions
        messageBuilder.append("<b>Permissions:</b><br>");
        if (isPermissionGranted && isNotificationPermissionGranted) {
            messageBuilder.append("- All required permissions granted.<br>");
        } else {
            messageBuilder.append("- Permissions are missing. Please grant them in app settings.<br>");
        }

        // Status of forwarding settings
        messageBuilder.append("<br><b>Forwarding Settings:</b><br>");
        if (isWebhookConfigured) {
            messageBuilder.append("- Webhook is ON.<br>");
        } else {
            messageBuilder.append("- Webhook is OFF. URL not configured.<br>");
        }

        if (isTelegramConfigured) {
            messageBuilder.append("- Telegram is ON.<br>");
        } else {
            messageBuilder.append("- Telegram is OFF. Bot token or Chat ID not configured.<br>");
        }

        if (!isWebhookConfigured && !isTelegramConfigured) {
            messageBuilder.append("<br>No forwarding endpoints are configured. Messages will be logged locally but not forwarded.");
        }

        // Display the detailed status in a dialog
        AlertDialog dialog = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialog_App)
                .setTitle(title)
                .setMessage(Html.fromHtml(messageBuilder.toString(), Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("OK", (dialogInterface, which) -> dialogInterface.dismiss())
                .show();

        // Set button color
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.button_normal));
        }
    }

    private void updateStatusIconColor() {
        ImageView statusInfoIcon = findViewById(R.id.statusInfoIcon);
        String status = statusTextView.getText().toString();
        int colorRes;

        switch (status) {
            case "Service running":
                colorRes = R.color.text_accent;
                break;
            case "Settings saved":
                colorRes = R.color.status_green;
                break;
            case "Permissions required":
                colorRes = R.color.text_pink;
                break;
            default:
                colorRes = R.color.text_secondary;
                break;
        }
        statusInfoIcon.setColorFilter(ContextCompat.getColor(this, colorRes));
    }

    private void showMenuDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialog_App);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_menu, null);
        builder.setView(dialogView);

        // Get references to the TextViews
        TextView aboutItem = dialogView.findViewById(R.id.aboutItem);
        TextView contactItem = dialogView.findViewById(R.id.contactItem);
        TextView termsItem = dialogView.findViewById(R.id.termsItem);
        TextView privacyItem = dialogView.findViewById(R.id.privacyItem);

        final AlertDialog menuDialog = builder.create();
        Window window = menuDialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        View.OnClickListener infoClickListener = v -> {
            // Dismiss the menu dialog first
            menuDialog.dismiss();

            // Store your titles and messages in a Map for a cleaner lookup
            Map<Integer, String[]> infoMap = new HashMap<>();
            infoMap.put(R.id.aboutItem, new String[]{"About", "This app helps you track your payments by forwarding incoming SMS messages to your personal webhook or Telegram account."});
            infoMap.put(R.id.contactItem, new String[]{"Contact", "You can contact the developer <a href=\"https://t.me/bharath_boy\">Bharath</a> for support or feedback."});
            infoMap.put(R.id.termsItem, new String[]{"Terms and Conditions", "All data is processed locally on your device.<br><br><b>Disclaimer:</b> The developer is not responsible for any misuse of the app."});
            infoMap.put(R.id.privacyItem, new String[]{"Privacy Policy", "This app does not collect any personal data.<br><br>All information is stored securely on your device and sent directly to your configured endpoints."});

            // Retrieve the title and message from the Map based on the clicked item's ID
            String[] info = infoMap.get(v.getId());
            if (info != null) {
                String title = info[0];
                String message = info[1];
                showInfoDialog(title, message);
            }
        };

        aboutItem.setOnClickListener(infoClickListener);
        contactItem.setOnClickListener(infoClickListener);
        termsItem.setOnClickListener(infoClickListener);
        privacyItem.setOnClickListener(infoClickListener);

        menuDialog.show();
    }

    private void showInfoDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_info_message, null);
        builder.setView(dialogView);

        TextView titleTextView = dialogView.findViewById(R.id.dialogTitleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.dialogMessageTextView);
        Button okButton = dialogView.findViewById(R.id.dialogOkButton);

        titleTextView.setText(title);
        messageTextView.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
        messageTextView.setMovementMethod(LinkMovementMethod.getInstance()); // Make links clickable

        final AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        okButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }


    private void saveSettingsFromDialog(String key, String value) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private void loadSettingsForDialog(EditText editText, String key) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String value = sharedPreferences.getString(key, "");
        editText.setText(value);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        loadMessagesFromPrefs();
        if (!isReceiverRegistered) {
            // Register receiver for new messages
            IntentFilter messageFilter = new IntentFilter("com.example.paymenttracker.NEW_MESSAGE");
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

            // Register receiver for forwarding status updates
            IntentFilter statusFilter = new IntentFilter(SmsForwardingService.ACTION_FORWARDING_STATUS);
            statusReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (SmsForwardingService.ACTION_FORWARDING_STATUS.equals(intent.getAction())) {
                        String errorMessage = intent.getStringExtra(SmsForwardingService.EXTRA_MESSAGE);
                        runOnUiThread(() -> {
                            onTelegramSendFailure(errorMessage);
                        });
                    }
                }
            };


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(messageReceiver, messageFilter, Context.RECEIVER_NOT_EXPORTED);
                registerReceiver(statusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(messageReceiver, messageFilter);
                registerReceiver(statusReceiver, statusFilter);
            }
            isReceiverRegistered = true;
            Log.d("MainActivity", "Message and status receivers registered.");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isReceiverRegistered) {
            unregisterReceiver(messageReceiver);
            unregisterReceiver(statusReceiver); // Unregister the status receiver
            isReceiverRegistered = false;
        }
    }

    private void loadMessagesFromPrefs() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        String messagesString = sharedPreferences.getString(MESSAGES, "[]");
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
                Log.d("MainActivity", "Detected old messages format — migrating to JSON storage.");
                String[] tokens = messagesString.split("\\|\\|\\|");
                for (int i = 0; i + 3 < tokens.length; i += 4) {
                    String sender = tokens[i];
                    String body = tokens[i + 1];
                    String status = tokens[i + 2];
                    String timestamp = tokens[i + 3];

                    if ("INVALID_FORMAT".equalsIgnoreCase(status) || "INVALID".equalsIgnoreCase(status)) {
                        status = "IGNORED";
                    }

                    loadedMessages.add(new Message(sender, body, status, timestamp));
                    Log.d("MainActivity", "Migrated message: " + body);
                }

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

        Collections.sort(loadedMessages, (m1, m2) -> {
            long t1 = 0, t2 = 0;
            try { t1 = Long.parseLong(m1.timestamp); } catch (Exception ignored) {}
            try { t2 = Long.parseLong(m2.timestamp); } catch (Exception ignored) {}
            return Long.compare(t2, t1);
        });

        messagesList.clear();
        messagesList.addAll(loadedMessages);

        // This is the new logic to show/hide the placeholder
        if (messagesList.isEmpty()) {
            recyclerViewMessages.setVisibility(View.GONE);
            emptyStateTextView.setVisibility(View.VISIBLE);
        } else {
            recyclerViewMessages.setVisibility(View.VISIBLE);
            emptyStateTextView.setVisibility(View.GONE);
        }

        if (messageAdapter != null) messageAdapter.notifyDataSetChanged();
        Log.d("MainActivity", "Loaded " + messagesList.size() + " messages from prefs and sorted them.");
    }
    public void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissionsToRequest.isEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            startForwardingService();
            updateStatusIconColor();
            checkBatteryOptimization();
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog();
            }
        }
    }

    private void showBatteryOptimizationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_battery_optimization, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Button dialogCancelButton = dialogView.findViewById(R.id.dialogCancelButton);
        Button dialogSettingsButton = dialogView.findViewById(R.id.dialogSettingsButton);

        dialogCancelButton.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Battery optimization is enabled. The app may be killed by the system.", Toast.LENGTH_LONG).show();
        });

        dialogSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            String packageName = getPackageName();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
            dialog.dismiss();
        });

        dialog.show();
    }

    public void startForwardingService() {
        Intent serviceIntent = new Intent(this, SmsForwardingService.class);
        startForegroundService(serviceIntent);
        statusTextView.setText("Service running");
        updateStatusIconColor();
        Log.d("MainActivity", "Foreground service started successfully.");
    }

    private void testWebhook(String webhookUrl, String secretKey) {
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

    private void testTelegram(String botToken, String chatId) {
        if (botToken == null || botToken.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
            Toast.makeText(this, "Bot token or Chat ID is empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Use the TelegramSender instance with the callback
        telegramSender.setCallback(this);
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("amount", "1.00");
            jsonObject.put("upiRefId", "TEST1234567890");
            jsonObject.put("senderName", "Test User");
            jsonObject.put("senderVpa", "test@upi");
            jsonObject.put("fullSmsBody", "This is a test message from your app.");
            jsonObject.put("bank", "Test Bank");
            jsonObject.put("dateTime", "2023-10-27 10:30:00");
            jsonObject.put("notes", "Testing");

            String jsonPayload = jsonObject.toString(4);

            telegramSender.sendPaymentDetails(botToken, chatId, jsonPayload);

        } catch (Exception e) {
            Log.e("TelegramTest", "Error sending Telegram test message", e);
            showErrorMessage("Error creating Telegram test message: " + e.getMessage());
        }
    }

    @Override
    public void onTelegramSendSuccess() {
        // Hide the notification icon on success
        notificationButton.setVisibility(View.GONE);
        Toast.makeText(MainActivity.this, "Test Telegram OK. Check the channel.", Toast.LENGTH_LONG).show();
        Log.i("MainActivity", "Telegram message sent successfully.");
    }

    @Override
    public void onTelegramSendFailure(String errorMessage) {
        // Show the notification icon and set a click listener to show the error
        notificationButton.setVisibility(View.VISIBLE);
        notificationButton.setOnClickListener(v -> showErrorMessage(errorMessage));
        Toast.makeText(MainActivity.this, "Test Telegram Failed. Tap the Error icon for details.", Toast.LENGTH_LONG).show();
        Log.e("MainActivity", "Telegram message failed to send: " + errorMessage);
    }

    private void showErrorMessage(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_error_message, null);
        builder.setView(dialogView);

        TextView titleTextView = dialogView.findViewById(R.id.dialogTitleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.dialogMessageTextView);
        Button okButton = dialogView.findViewById(R.id.dialogOkButton);

        titleTextView.setText(R.string.forwarding_error_title);
        messageTextView.setText(message);

        final AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        okButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
