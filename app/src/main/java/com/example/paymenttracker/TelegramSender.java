package com.example.paymenttracker;

import android.util.Log;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import java.io.IOException;

/**
 * Handles sending payment JSON as text to a Telegram chat via sendMessage.
 */
public class TelegramSender {

    private static final String TAG = "TelegramSender";
    private static final String TELEGRAM_API_BASE_URL = "https://api.telegram.org/bot";

    /**
     * Sends the provided JSON payload as a text message to the given chat using sendMessage.
     *
     * @param botToken    Telegram bot token
     * @param chatId      Chat id (user, group or channel)
     * @param jsonPayload The JSON string to send as text
     */
    public void sendPaymentDetails(String botToken, String chatId, String jsonPayload) {
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            Log.e(TAG, "Bot token or chat ID is missing. Cannot send message to Telegram.");
            return;
        }

        if (jsonPayload == null) {
            jsonPayload = "{}";
        }

        OkHttpClient client = new OkHttpClient();
        String telegramUrl = TELEGRAM_API_BASE_URL + botToken + "/sendMessage";

        try {
            // Wrap the JSON in <pre> to preserve formatting in Telegram
            String formattedMessage = "<pre>" + jsonPayload + "</pre>";

            // Build the JSON request body for Telegram API
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("chat_id", chatId);
            jsonObject.put("text", formattedMessage);
            jsonObject.put("parse_mode", "HTML");

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(jsonObject.toString(), JSON);

            Request request = new Request.Builder()
                    .url(telegramUrl)
                    .post(body)
                    .build();

            // Run network call off the main thread
            new Thread(() -> {
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Failed to send JSON text to Telegram: " + response.code() + " " + response.message());
                        if (response.body() != null) {
                            Log.e(TAG, "Response body: " + response.body().string());
                        }
                    } else {
                        Log.i(TAG, "Successfully sent JSON text to Telegram.");
                        if (response.body() != null) {
                            Log.d(TAG, "Response body: " + response.body().string());
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException while sending JSON to Telegram: " + e.getMessage(), e);
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error creating Telegram message JSON: " + e.getMessage(), e);
        }
    }
}
