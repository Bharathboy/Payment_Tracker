// PASTE THIS ENTIRE BLOCK INTO PaymentDetails.java
package com.example.paymenttracker;

// This is a simple "Plain Old Java Object" (POJO).
// Its only job is to hold the data we parse from the SMS.
public class PaymentDetails {
    public String amount;
    public String upiRefId;
    public String senderName;
    public String senderVpa;
}