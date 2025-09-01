
package com.example.paymenttracker;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
// import android.view.ViewTreeObserver; // REMOVED
import android.widget.Button;
import android.widget.EditText;
// import android.widget.ImageView; // REMOVED
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

// import jp.wasabeef.blurry.Blurry; // REMOVED

// OkHttp imports will be re-added when we implement testWebhook in the dialog
// import org.json.JSONException;
// import org.json.JSONObject;
// import java.io.IOException;
// import okhttp3.Call;
// import okhttp3.Callback;
// import okhttp3.MediaType;
// import okhttp3.OkHttpClient;
// import okhttp3.Request;
// import okhttp3.RequestBody;
// import okhttp3.Response;


public class MainActivity extends AppCompatActivity {

    private List<Message> messagesList = new ArrayList<>();
    private MessageAdapter messageAdapter;
    private RecyclerView recyclerViewMessages;
    private BroadcastReceiver newMessageReceiver;

    private TextView statusTextView;
    // private ImageView blurBackground; // REMOVED
    private View rootLayout;
    private Button settingsButton;


    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String WEBHOOK_URL = "webhookUrl";
    public static final String SECRET_KEY = "secretKey";

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
        // blurBackground = findViewById(R.id.blur_background); // REMOVED
        rootLayout = findViewById(R.id.root_layout);
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        messagesList.addAll(getSampleMessages());
        messageAdapter = new MessageAdapter(messagesList);
        recyclerViewMessages.setAdapter(messageAdapter);

        // BroadcastReceiver for new messages
        newMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.paymenttracker.NEW_MESSAGE".equals(intent.getAction())) {
                    Message newMessage = intent.getParcelableExtra("com.example.paymenttracker.MESSAGE_OBJECT");
                    if (newMessage != null) {
                        messagesList.add(0, newMessage); // Add to top
                        messageAdapter.notifyItemInserted(0);
                        recyclerViewMessages.scrollToPosition(0);
                    }
                }
            }
        };

        // Place settingsButton click listener inside onCreate
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
                Toast.makeText(MainActivity.this, "Test button clicked (Not yet implemented)", Toast.LENGTH_SHORT).show();
            });
            dialog.show();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(newMessageReceiver);
    }

        // REMOVED ViewTreeObserver block for Blurry
        // rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
        //     @Override
        //     public void onGlobalLayout() {
        //         rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        //         Blurry.with(MainActivity.this)
        //                 .radius(25)
        //                 .sampling(2)
        //                 .capture(rootLayout)
        //                 .into(blurBackground);
        //     }
        // });

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

            dialogCancelButton.setOnClickListener(dv -> dialog.dismiss());
            
    });
        String secretKey = sharedPreferences.getString(SECRET_KEY, "");

        urlEditText.setText(webhookUrl);
        keyEditText.setText(secretKey);
    }
    
    // Added saveSettingsFromDialog (implementation from previous interaction)
    private void saveSettingsFromDialog(String webhookUrl, String secretKey) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(WEBHOOK_URL, webhookUrl);
        editor.putString(SECRET_KEY, secretKey);
        editor.apply();
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
    }

    private java.util.List<Message> getSampleMessages() {
        java.util.List<Message> messages = new java.util.ArrayList<>();
        messages.add(new Message(
            "+919741430392",
            "Your a/c is credited with Rs10.99 on 31-08-2025 from Test User with VPA test@upi UPI Ref No 123456789012",
            "IGNORED",
            @Override
            protected void onPause() {
                super.onPause();
                unregisterReceiver(newMessageReceiver);
            }
    public void startForwardingService() {
