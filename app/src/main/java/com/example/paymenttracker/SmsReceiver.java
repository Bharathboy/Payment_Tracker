// PASTE THIS ENTIRE BLOCK INTO SmsReceiver.java
package com.example.paymenttracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // This is now the only job of the receiver:
        // When an SMS is received, create an intent for our service,
        // copy the SMS data into it, and start the service in the foreground.
        // This is much more reliable than doing the work directly in the receiver.
        Intent serviceIntent = new Intent(context, SmsForwardingService.class);
        serviceIntent.setAction(intent.getAction());
        serviceIntent.putExtras(intent.getExtras());

        context.startForegroundService(serviceIntent);
    }
}