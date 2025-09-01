
package com.example.paymenttracker;

public class Message {
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
}
