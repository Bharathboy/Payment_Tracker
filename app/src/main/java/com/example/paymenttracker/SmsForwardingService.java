package com.example.paymenttracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Forwarding Active")
                .setContentText("Listening for incoming payment SMS...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        if (intent != null && intent.getExtras() != null) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                String messageBody = smsMessage.getMessageBody();
                String originatingAddress = smsMessage.getOriginatingAddress();
                long timestampMillis = smsMessage.getTimestampMillis();

                PaymentDetails details = SmsParser.parse(messageBody);
                String dateString = String.valueOf(timestampMillis);

                Message newMessage;
                if (details != null) {
                    SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFS, Context.MODE_PRIVATE);
                    String webhookUrl = sharedPreferences.getString(MainActivity.WEBHOOK_URL, "");
                    if (webhookUrl.isEmpty()) {
                        Log.d(TAG, "Parsed payment SMS but no webhook URL is set.");
                        newMessage = new Message(originatingAddress, messageBody, "WEBHOOK NOT SET", dateString);
                    } else {
                        Log.d(TAG, "Successfully parsed payment SMS in service.");
                        sendWebhook(this, details, messageBody, webhookUrl, sharedPreferences.getString(MainActivity.SECRET_KEY, ""));
                        newMessage = new Message(originatingAddress, messageBody, "SUBMITTED", dateString);
                    }
                } else {
                    Log.d(TAG, "SMS did not parse into PaymentDetails: " + messageBody);
                    newMessage = new Message(originatingAddress, messageBody, "IGNORED", dateString);
                }

                saveMessageToPrefs(newMessage);

                Intent broadcastIntent = new Intent("com.example.paymenttracker.NEW_MESSAGE");
                broadcastIntent.putExtra("com.example.paymenttracker.MESSAGE_OBJECT", newMessage);
                sendBroadcast(broadcastIntent);
                Log.d(TAG, "New message globally broadcasted to MainActivity from sender: " + originatingAddress);
            }
        }
        return START_STICKY;
    }

    private void saveMessageToPrefs(Message message) {
        SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFS, Context.MODE_PRIVATE);
        String messagesString = sharedPreferences.getString(MainActivity.MESSAGES, "");
        StringBuilder sb = new StringBuilder(messagesString);
        if (!messagesString.isEmpty()) {
            sb.append("|||"); // Delimiter
        }
        sb.append(message.toString());

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(MainActivity.MESSAGES, sb.toString());
        editor.apply();
    }

    private void sendWebhook(Context context, PaymentDetails details, String fullSms, String webhookUrl, String secretKey) {
        JSONObject jsonPayload = new JSONObject();
        try {
            jsonPayload.put("amount_received", details.amount);
            jsonPayload.put("upi_ref_id", details.upiRefId);
            jsonPayload.put("sender_name", details.senderName);
            jsonPayload.put("sender_vpa", details.senderVpa);
            jsonPayload.put("full_sms_body", fullSms);
        } catch (JSONException e) {
            mainHandler.post(() -> Toast.makeText(context, "Error creating JSON payload", Toast.LENGTH_SHORT).show());
            Log.e(TAG, "Error creating JSON payload", e);
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> Toast.makeText(context, "Webhook failed to send", Toast.LENGTH_SHORT).show());
                Log.e(TAG, "Webhook failed to send", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    mainHandler.post(() -> Toast.makeText(context, "Webhook sent successfully", Toast.LENGTH_SHORT).show());
                } else {
                    mainHandler.post(() -> Toast.makeText(context, "Webhook failed to send", Toast.LENGTH_SHORT).show());
                }
                Log.d(TAG, "Webhook sent. Response code: " + response.code());
                response.close();
            }
        });
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarder Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}