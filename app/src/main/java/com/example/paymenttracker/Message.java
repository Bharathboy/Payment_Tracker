
package com.example.paymenttracker;

import android.os.Parcel;
import android.os.Parcelable;

public class Message implements Parcelable {
    public String sender;
    public String content;
    public String status;
    public String date;

    public Message(String sender, String content, String status, String date) {
        this.sender = sender;
        this.content = content;
        this.status = status;
        this.date = date;
    }

    // Parcelable implementation
    protected Message(Parcel in) {
        sender = in.readString();
        content = in.readString();
        status = in.readString();
        date = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(sender);
        dest.writeString(content);
        dest.writeString(status);
        dest.writeString(date);
    }

    @Override
    public int describeContents() {
        return 0;
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
}
