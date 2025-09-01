package com.example.paymenttracker;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Message implements Parcelable {
    public String sender;
    public String content;
    public String status;
    public String timestamp;

    public Message(String sender, String content, String status, String timestamp) {
        this.sender = sender;
        this.content = content;
        this.status = status;
        this.timestamp = timestamp;
    }

    protected Message(Parcel in) {
        sender = in.readString();
        content = in.readString();
        status = in.readString();
        timestamp = in.readString();
    }

    public static final Creator<Message> CREATOR = new Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }
        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(sender);
        dest.writeString(content);
        dest.writeString(status);
        dest.writeString(timestamp);
    }

    public String getFormattedTimestamp() {
        try {
            long timestampMillis = Long.parseLong(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestampMillis));
        } catch (NumberFormatException e) {
            return timestamp; // Return raw string if parsing fails
        }
    }

    @Override
    public String toString() {
        return sender + "|||" + content + "|||" + status + "|||" + timestamp;
    }

    public static Message fromString(String messageString) {
        String[] parts = messageString.split("\\|\\|\\|");
        if (parts.length == 4) {
            return new Message(parts[0], parts[1], parts[2], parts[3]);
        }
        return new Message("N/A", "Invalid message format", "ERROR", "");
    }
}