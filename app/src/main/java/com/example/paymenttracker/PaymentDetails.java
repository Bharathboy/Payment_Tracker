package com.example.paymenttracker;

import java.io.Serializable;

// This is a simple "Plain Old Java Object" (POJO).
// Its only job is to hold the data we parse from the SMS.
public class PaymentDetails implements Serializable {
    public String amount;
    public String upiRefId;
    public String senderName;
    public String senderVpa;
    public String fullSmsBody;
    public String bank;
    public String dateTime;
    public String notes;
}
