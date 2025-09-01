package com.example.paymenttracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
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

public class SmsForwardingService extends Service {
    private static final String TAG = "SmsForwardingService";
    public static final String CHANNEL_ID = "SmsForwarderChannel";
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called.");
        showForegroundNotification();

        if (intent != null && intent.getExtras() != null) {
            Log.d(TAG, "Intent received. Processing SMS messages.");
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                String messageBody = smsMessage.getMessageBody();
                String originatingAddress = smsMessage.getOriginatingAddress();
                long timestampMillis = smsMessage.getTimestampMillis();
                String dateString = String.valueOf(timestampMillis);

                if (messageBody == null || originatingAddress == null) {
                    Log.w(TAG, "Received SMS with null body or sender. Skipping...");
                    continue;
                }

                PaymentDetails details = SmsParser.parse(messageBody);
                Message newMessage;

                SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFS, Context.MODE_PRIVATE);
                String webhookUrl = sharedPreferences.getString(MainActivity.WEBHOOK_URL, "");
                String telegramBotToken = sharedPreferences.getString(MainActivity.TELEGRAM_BOT_TOKEN, "");
                String telegramChatId = sharedPreferences.getString(MainActivity.TELEGRAM_CHAT_ID, "");
                String secretKey = sharedPreferences.getString(MainActivity.SECRET_KEY, "");

                if (details != null) {
                    if ((webhookUrl == null || webhookUrl.isEmpty()) &&
                            (telegramBotToken == null || telegramBotToken.isEmpty() || telegramChatId == null || telegramChatId.isEmpty())) {
                        Log.d(TAG, "Parsed payment SMS but no forwarding options set.");
                        newMessage = new Message(originatingAddress, messageBody, "NO_FORWARDER_SET", dateString);
                    } else {
                        Log.d(TAG, "Successfully parsed payment SMS.");

                        if (webhookUrl != null && !webhookUrl.isEmpty()) {
                            sendWebhook(details, messageBody, webhookUrl, secretKey);
                        }

                        if (telegramBotToken != null && !telegramBotToken.isEmpty() && telegramChatId != null && !telegramChatId.isEmpty()) {
                            sendTelegramMessage(details, messageBody, telegramBotToken, telegramChatId);
                        }

                        newMessage = new Message(originatingAddress, messageBody, "SUBMITTED", dateString);
                    }
                } else {
                    Log.d(TAG, "SMS ignored (not a payment message): " + messageBody);
                    newMessage = new Message(originatingAddress, messageBody, "IGNORED", dateString);
                }

                saveMessageToPrefs(newMessage);
                broadcastNewMessage(newMessage);
            }
        }

        return START_STICKY;
    }

    private void broadcastNewMessage(Message message) {
        Intent broadcastIntent = new Intent("com.example.paymenttracker.NEW_MESSAGE");
        broadcastIntent.putExtra("sender", message.sender != null ? message.sender : "UNKNOWN");
        broadcastIntent.putExtra("body", message.content != null ? message.content : "");
        broadcastIntent.putExtra("status", message.status != null ? message.status : "UNKNOWN");
        broadcastIntent.putExtra("timestamp", message.timestamp != null ? message.timestamp : String.valueOf(System.currentTimeMillis()));
        sendBroadcast(broadcastIntent);
        Log.d(TAG, "New message broadcasted: " + message.sender);
    }

    private void saveMessageToPrefs(Message message) {
        SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFS, Context.MODE_PRIVATE);
        String messagesJson = sharedPreferences.getString(MainActivity.MESSAGES, "[]");

        JSONArray existingArray;
        try {
            existingArray = new JSONArray(messagesJson);
        } catch (JSONException e) {
            existingArray = new JSONArray();
        }

        JSONObject obj = new JSONObject();
        try {
            obj.put("sender", message.sender != null ? message.sender : "UNKNOWN");
            obj.put("body", message.content != null ? message.content : "");
            obj.put("status", message.status != null ? message.status : "UNKNOWN");
            obj.put("timestamp", message.timestamp != null ? message.timestamp : String.valueOf(System.currentTimeMillis()));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating message JSON", e);
        }

        JSONArray newArray = new JSONArray();
        newArray.put(obj);
        for (int i = 0; i < existingArray.length(); i++) {
            try {
                newArray.put(existingArray.get(i));
            } catch (JSONException ignore) {
            }
        }

        sharedPreferences.edit().putString(MainActivity.MESSAGES, newArray.toString()).apply();
        Log.d(TAG, "Message saved to SharedPreferences successfully.");
    }

    private void sendWebhook(PaymentDetails details, String fullSms, String webhookUrl, String secretKey) {
        JSONObject jsonPayload = new JSONObject();
        try {
            jsonPayload.put("amount_received", details.amount);
            jsonPayload.put("upi_ref_id", details.upiRefId);
            jsonPayload.put("sender_name", details.senderName);
            jsonPayload.put("sender_vpa", details.senderVpa);
            jsonPayload.put("full_sms_body", fullSms);
        } catch (JSONException e) {
            showToast("Error creating JSON payload");
            Log.e(TAG, "Error creating JSON payload", e);
            return;
        }

        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonPayload.toString(), JSON);

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .addHeader("X-My-App-Signature", secretKey != null ? secretKey : "")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("Webhook failed to send");
                Log.e(TAG, "Webhook failed to send", e);
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                showToast(response.isSuccessful() ? "Webhook sent successfully" : "Webhook failed");
                Log.d(TAG, "Webhook response code: " + response.code());
                response.close();
            }
        });
    }

    private void sendTelegramMessage(PaymentDetails details, String fullSms, String botToken, String chatId) {
        String telegramUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        OkHttpClient client = new OkHttpClient();

        String jsonString;
        try {
            JSONObject json = new JSONObject();
            json.put("amount", details.amount != null ? details.amount : "");
            json.put("upiRefId", details.upiRefId != null ? details.upiRefId : "");
            json.put("senderName", details.senderName != null ? details.senderName : "");
            json.put("senderVpa", details.senderVpa != null ? details.senderVpa : "");
            json.put("fullSmsBody", fullSms != null ? fullSms : "");
            json.put("bank", details.bank != null ? details.bank : "");
            json.put("dateTime", details.dateTime != null ? details.dateTime : "");
            json.put("notes", details.notes != null ? details.notes : "");
            jsonString = "<pre>" + json.toString(4) + "</pre>"; // Pretty print with HTML parse mode
        } catch (JSONException e) {
            showToast("Error creating Telegram payload");
            Log.e(TAG, "Error creating JSON payload", e);
            return;
        }

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("chat_id", chatId);
            requestJson.put("text", jsonString);
            requestJson.put("parse_mode", "HTML");
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request JSON", e);
            return;
        }

        MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

        Request request = new Request.Builder()
                .url(telegramUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showToast("Failed to send message to Telegram");
                Log.e(TAG, "Failed to send message to Telegram", e);
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    showToast("Message sent to Telegram");
                    Log.d(TAG, "Telegram response code: " + response.code());
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "empty";
                    showToast("Telegram send failed");
                    Log.e(TAG, "Error: " + response.code() + ", Body: " + errorBody);
                }
                response.close();
            }
        });
    }

    private void showForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Forwarding Active")
                .setContentText("Listening for payment SMS...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS Forwarder Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
            Log.d(TAG, "Notification channel created.");
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
