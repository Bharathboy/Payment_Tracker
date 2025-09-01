package com.example.paymenttracker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SmsParser - robust parser that tries to populate your package-level PaymentDetails
 * using reflection so it works whether PaymentDetails.amount is BigDecimal or String,
 * whether rawAmount exists or not, and whether fields are public or accessible via setters.
 *
 * Returns null unless a known payment pattern is found (amount + at least one of ref/vpa/sender).
 */
public class SmsParser {

    // Patterns used in a layered extraction approach
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)");
    private static final Pattern REF_PATTERN = Pattern.compile("(?i)(?:UPI\\s*Ref(?:\\s*No)?|Ref\\.No|Ref\\s*No|Ref|Txn ID|TXN ID)[:\\s-]*([A-Za-z0-9-]+)");
    private static final Pattern VPA_PATTERN = Pattern.compile("(?i)\\b([\\w.+-]+@[\\w.-]+)\\b"); // typical vpa like somebody@upi
    private static final Pattern SENDER_FROM_PATTERN = Pattern.compile("(?i)\\bfrom\\s+([^\\n,\\(]+)");
    private static final Pattern SENDER_BY_PATTERN = Pattern.compile("(?i)\\bby\\s+([^\\n,\\(]+)");
    private static final Pattern CREDITED_TO_PATTERN = Pattern.compile("(?i)credited (?:to|with)\\s+([^\\n,\\(]+)");
    private static final Pattern RECEIVED_FROM_PATTERN = Pattern.compile("(?i)received from\\s+([^\\n,\\(]+)");

    /**
     * Attempt to parse an SMS body for payment-related info.
     *
     * @param smsBody raw SMS text
     * @return com.example.paymenttracker.PaymentDetails if a known payment pattern is found; otherwise null
     */
    public static PaymentDetails parse(String smsBody) {
        if (smsBody == null || smsBody.trim().isEmpty()) return null;

        String s = smsBody.trim();

        // 1) Amount (string form)
        String amountString = null;
        BigDecimal amountBig = null;
        Matcher mAmt = AMOUNT_PATTERN.matcher(s);
        if (mAmt.find()) {
            amountString = mAmt.group(1);
            amountBig = parseAmountToBigDecimal(amountString);
        }

        // 2) UPI / reference id
        String upiRefId = null;
        Matcher mRef = REF_PATTERN.matcher(s);
        if (mRef.find()) {
            upiRefId = mRef.group(1).trim();
        } else {
            Matcher looseRef = Pattern.compile("\\b([A-Za-z0-9]{8,30})\\b").matcher(s);
            while (looseRef.find()) {
                String candidate = looseRef.group(1);
                // skip obvious amounts (pure digits)
                if (candidate.matches("\\d+")) continue;
                if (candidate.matches(".*[A-Za-z-].*")) {
                    upiRefId = candidate;
                    break;
                }
            }
        }

        // 3) VPA (if present anywhere)
        String vpa = null;
        Matcher mVpa = VPA_PATTERN.matcher(s);
        if (mVpa.find()) {
            vpa = mVpa.group(1).trim();
        }

        // 4) Sender name - try several heuristics
        String senderName = extractSenderName(s);

        // If no amount or none of the other identifiers, must return null (per your request)
        boolean hasAmount = amountBig != null || (amountString != null && !amountString.isEmpty());
        boolean hasRefOrVpaOrName = (upiRefId != null && !upiRefId.isEmpty())
                || (vpa != null && !vpa.isEmpty())
                || (senderName != null && !senderName.isEmpty());

        if (!hasAmount || !hasRefOrVpaOrName) {
            return null;
        }

        // Build and populate package-level PaymentDetails using reflection (works with String or BigDecimal etc.)
        PaymentDetails details = instantiatePaymentDetails();
        if (details == null) {
            // Could not instantiate; return null to avoid NPE in caller
            return null;
        }

        // Try to set fields or call setters if they exist. Tolerant behavior: missing fields are ignored.
        safeSet(details, "amount", amountBig != null ? amountBig : amountString);
        safeSet(details, "rawAmount", amountString);
        safeSet(details, "senderName", senderName);
        safeSet(details, "senderVpa", vpa);
        safeSet(details, "upiRefId", upiRefId);

        return details;
    }

    // instantiate PaymentDetails via no-arg constructor
    private static PaymentDetails instantiatePaymentDetails() {
        try {
            return PaymentDetails.class.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // If PaymentDetails isn't public/no-arg, we can't proceed safely
            return null;
        }
    }

    /**
     * Try to set a property on the target object using:
     * 1) setter method setXxxx(...)
     * 2) direct field access if available
     *
     * This will handle common cases where PaymentDetails.amount is BigDecimal or String.
     */
    private static void safeSet(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null || value == null) return;

        Class<?> cls = target.getClass();
        String setterName = "set" + capitalize(fieldName);

        // 1) Try setter with exact type, or with String/BigDecimal conversions
        try {
            // attempt common setter signatures in order
            Method[] methods = cls.getMethods();
            for (Method m : methods) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    Object toPass = convertValueIfNeeded(value, paramType);
                    if (toPass != null) {
                        m.invoke(target, toPass);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // fallthrough to direct field access
        }

        // 2) Try direct field access (including private)
        try {
            Field f = findFieldRecursive(cls, fieldName);
            if (f != null) {
                f.setAccessible(true);
                Class<?> t = f.getType();
                Object toPass = convertValueIfNeeded(value, t);
                if (toPass != null) {
                    f.set(target, toPass);
                }
            }
        } catch (Exception ignored) {
            // give up silently; parser remains tolerant
        }
    }

    // Convert value to targetType if reasonably possible. Return null if not convertible.
    private static Object convertValueIfNeeded(Object value, Class<?> targetType) {
        if (value == null) return null;

        Class<?> src = value.getClass();

        if (targetType.isAssignableFrom(src)) {
            return value;
        }

        // if target is String, convert via toString
        if (targetType == String.class) {
            return value.toString();
        }

        // if target is BigDecimal and source is String or BigDecimal
        if (targetType == BigDecimal.class) {
            if (value instanceof BigDecimal) return value;
            if (value instanceof String) {
                try {
                    return new BigDecimal(((String) value).replace(",", ""));
                } catch (Exception e) {
                    return null;
                }
            }
        }

        // if target is primitive numeric types, try to convert from BigDecimal or String (rare for PaymentDetails)
        try {
            if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive()) {
                String s = value.toString().replace(",", "");
                if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(s);
                if (targetType == Long.class || targetType == long.class) return Long.parseLong(s);
                if (targetType == Double.class || targetType == double.class) return Double.parseDouble(s);
                if (targetType == Float.class || targetType == float.class) return Float.parseFloat(s);
                if (targetType == Short.class || targetType == short.class) return Short.parseShort(s);
            }
        } catch (Exception ignored) {
        }

        // otherwise unsupported conversion
        return null;
    }

    // Find field in class or its superclasses
    private static Field findFieldRecursive(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // Helper to parse string amount like "1,234.50" into BigDecimal
    private static BigDecimal parseAmountToBigDecimal(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replace(",", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Try multiple patterns and heuristics to fetch sender name
    private static String extractSenderName(String s) {
        // Only look for patterns that explicitly indicate a received payment
        List<Pattern> candidates = new ArrayList<>();
        candidates.add(RECEIVED_FROM_PATTERN);
        candidates.add(CREDITED_TO_PATTERN);

        for (Pattern p : candidates) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                String name = m.group(1).trim();
                // trim trailing keywords
                name = name.replaceAll("(?:on\\s.*|via\\s.*|with\\s.*)$", "").trim();
                // avoid returning something that looks like an account number or pure digits
                if (name.matches("^\\d+$")) {
                    continue;
                }
                // avoid returning a pure VPA
                if (VPA_PATTERN.matcher(name).find()) {
                    continue;
                }
                return name;
            }
        }

        return null;
    }
}
