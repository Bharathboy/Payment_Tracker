package com.example.paymenttracker;

public class Message {
    public String sender;
    public String message;
    public String status;
    public String date;

    public Message(String sender, String message, String status, String date) {
        this.sender = sender;
        this.message = message;
        this.status = status;
        this.date = date;
    }
}
