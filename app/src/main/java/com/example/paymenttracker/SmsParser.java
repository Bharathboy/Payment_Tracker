// PASTE THIS ENTIRE BLOCK INTO SmsParser.java
package com.example.paymenttracker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {

    // This method tries to match the SMS body against known bank formats.
    public static PaymentDetails parse(String smsBody) {

        // Pattern 1: For SMS like "credited with Rs... from Some Body... UPI Ref No 123..."
        // This is a very common format for many banks.
        Pattern pattern1 = Pattern.compile(
                "credited with Rs([\\d,]+\\.\\d{2}).*from (.*?)(?: on| with VPA (.*?)) UPI Ref No (\\d{12})"
        );
        Matcher matcher1 = pattern1.matcher(smsBody);
        if (matcher1.find()) {
            PaymentDetails details = new PaymentDetails();
            details.amount = matcher1.group(1).replace(",", "");
            details.senderName = matcher1.group(2).trim();
            details.senderVpa = matcher1.group(3) != null ? matcher1.group(3).trim() : "N/A";
            details.upiRefId = matcher1.group(4).trim();
            return details;
        }

        // Pattern 2: For SMS like "Rs. 10.00 credited to... VPA somebody@upi... Ref.No:123..."
        Pattern pattern2 = Pattern.compile(
                "Rs\\.([\\d,]+\\.\\d{2}) credited to.*VPA (.*?)(?: \\(UPI\\))?.*Ref\\.No:(\\d{12})"
        );
        Matcher matcher2 = pattern2.matcher(smsBody);
        if (matcher2.find()) {
            PaymentDetails details = new PaymentDetails();
            details.amount = matcher2.group(1).replace(",", "");
            details.senderName = "N/A"; // This format doesn't include the sender's name
            details.senderVpa = matcher2.group(2).trim();
            details.upiRefId = matcher2.group(3).trim();
            return details;
        }

        // You can add more patterns here for other bank formats if you need to.

        return null; // Return null if no known payment pattern matches the SMS
    }
}