package com.example.paymenttracker;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsParser {
    private static final Pattern PAYMENT_KEYWORDS = Pattern.compile(
            "(?i)(?:credited|debited|paid|txn for|transferred|received|sent|upi|imps|neft|rtgs|otp for)"
    );


    private static final Pattern RECEIVED_KEYWORDS = Pattern.compile(
            "(?i)(?:credited|received|to)"
    );
    private static final Pattern SENT_KEYWORDS = Pattern.compile(
            "(?i)(?:debited|sent|paid|from)"
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:Rs\\.?|INR|₹)\\b\\s*)?([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)"
    );

    private static final Pattern AMOUNT_CONTEXT_PATTERN = Pattern.compile(
            "(?i)(?:credited|debited|paid|txn for|transferred|received|amount)\\s*(?:of|by|with|:)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)"
    );

    private static final Pattern REF_PATTERN = Pattern.compile(
            "(?i)(?:UPI\\s*Ref(?:\\s*No)?|Ref\\.No|Ref\\s*No|Ref\\b|Txn ID|TXN ID|TxN|TXN|TRANS ID|Transaction ID)[:\\s-]*([A-Za-z0-9\\-]{4,40})"
    );

    private static final Pattern LOOSE_REF_CANDIDATE = Pattern.compile("\\b([A-Za-z0-9\\-]{6,40})\\b");

    private static final Pattern VPA_PATTERN = Pattern.compile("(?i)\\b([A-Za-z0-9._%+\\-]+@[A-Za-z0-9.-]+)\\b");

    private static final Pattern SENDER_FROM_PATTERN = Pattern.compile("(?i)\\bfrom\\s+([^\\n,\\.\\(]+)");
    private static final Pattern SENDER_BY_PATTERN = Pattern.compile("(?i)\\bby\\s+([^\\n,\\.\\(]+)");
    private static final Pattern SENDER_BENEFICIARY_PATTERN = Pattern.compile("(?i)\\b(beneficiary|beneficiary name|credited to|credited)\\s+([^\\n,\\.\\(]+)");
    private static final Pattern RECEIVED_FROM_PATTERN = Pattern.compile("(?i)received\\s+(?:by|from)\\s+([^\\n,\\.\\(]+)");
    private static final Pattern CREDITED_TO_PATTERN = Pattern.compile("(?i)credited\\s+(?:to|with)\\s+([^\\n,\\.\\(]+)");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("d-M-uu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("uuuu-M-d"),
            DateTimeFormatter.ofPattern("uuuu/M/d")
    );
    private static final Pattern DATE_CANDIDATE = Pattern.compile("\\b(\\d{1,4}[\\-/]\\d{1,2}[\\-/]\\d{1,4})\\b");

    private static final Pattern BANK_PATTERN = Pattern.compile("(?i)\\b(Kotak|Axis|HDFC|ICICI|SBI|Airtel Payments)(?:\\s+(?:Bank|AC|A/c|A/c|Account|Acct))?\\b");

    private static final Pattern ACCOUNT_TOKEN_PATTERN = Pattern.compile("(?:\\bX?\\*{0,4}\\d{2,}\\b|\\bAC\\b|\\bA/c\\b|\\bAcct\\b|\\bAccount\\b|\\b\\d{3,}\\b)", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> BANK_NORMALIZATION;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("kotak", "Kotak Mahindra Bank");
        m.put("kotak mahindra", "Kotak Mahindra Bank");
        m.put("hdfc", "HDFC Bank");
        m.put("hdfc bank", "HDFC Bank");
        m.put("icici", "ICICI Bank");
        m.put("icici bank", "ICICI Bank");
        m.put("axis", "Axis Bank");
        m.put("axis bank", "Axis Bank");
        m.put("sbi", "State Bank of India");
        m.put("state bank", "State Bank of India");
        m.put("state bank of india", "State Bank of India");
        m.put("pnb", "Punjab National Bank");
        m.put("punjab national", "Punjab National Bank");
        m.put("punjab national bank", "Punjab National Bank");
        m.put("bank of baroda", "Bank of Baroda");
        m.put("bob", "Bank of Baroda");
        m.put("canara", "Canara Bank");
        m.put("canara bank", "Canara Bank");
        m.put("yes bank", "Yes Bank");
        m.put("indusind", "IndusInd Bank");
        m.put("indusind bank", "IndusInd Bank");
        m.put("idbi", "IDBI Bank");
        m.put("idbi bank", "IDBI Bank");
        m.put("rbl", "RBL Bank");
        m.put("rbl bank", "RBL Bank");
        m.put("federal", "Federal Bank");
        m.put("federal bank", "Federal Bank");
        m.put("union bank", "Union Bank of India");
        m.put("union bank of india", "Union Bank of India");
        m.put("bank of india", "Bank of India");
        m.put("bank of maharashtra", "Bank of Maharashtra");
        m.put("indian bank", "Indian Bank");
        m.put("indian overseas bank", "Indian Overseas Bank");
        m.put("idfc", "IDFC FIRST Bank");
        m.put("idfc first", "IDFC FIRST Bank");
        m.put("au", "AU Small Finance Bank");
        m.put("au small", "AU Small Finance Bank");
        m.put("au small finance", "AU Small Finance Bank");
        m.put("south indian", "South Indian Bank");
        m.put("south indian bank", "South Indian Bank");
        m.put("bandhan", "Bandhan Bank");
        m.put("bandhan bank", "Bandhan Bank");
        m.put("csb", "CSB Bank");
        m.put("csb bank", "CSB Bank");
        m.put("airtel payments", "Airtel Payments Bank");
        m.put("airtel payments bank", "Airtel Payments Bank");
        m.put("paytm", "Paytm Payments Bank");
        m.put("paytm payments", "Paytm Payments Bank");
        m.put("paytm payments bank", "Paytm Payments Bank");
        m.put("india post", "India Post Payments Bank");
        m.put("india post payments", "India Post Payments Bank");
        m.put("karnataka", "Karnataka Bank");
        m.put("karnataka bank", "Karnataka Bank");
        BANK_NORMALIZATION = Collections.unmodifiableMap(m);
    }

    private static final Set<String> IGNORED_BANK_WORDS = new HashSet<>(Arrays.asList("your", "my", "our", "in", "to", "a", "the"));

    public static PaymentDetails parse(String smsBody) {
        if (smsBody == null || smsBody.trim().isEmpty()) return null;

        Map<String, Object> map = parseToMap(smsBody);
        if (map == null) return null;

        Object amountVal = map.get("amountBig");
        String amountRaw = (String) map.get("rawAmount");
        String ref = (String) map.get("upiRefId");
        String vpa = (String) map.get("senderVpa");
        String sender = (String) map.get("senderName");

        boolean hasAmount = amountVal != null || (amountRaw != null && !amountRaw.isEmpty());
        boolean hasRefOrVpaOrName = (ref != null && !ref.isEmpty()) || (vpa != null && !vpa.isEmpty()) || (sender != null && !sender.isEmpty());
        if (!hasAmount || !hasRefOrVpaOrName) {
            return null;
        }

        PaymentDetails details = instantiatePaymentDetails();
        if (details == null) {
            return null;
        }

        safeSet(details, "amount", amountVal != null ? amountVal : amountRaw);
        safeSet(details, "rawAmount", amountRaw);
        safeSet(details, "senderName", sender);
        safeSet(details, "senderVpa", vpa);
        safeSet(details, "upiRefId", ref);
        safeSet(details, "dateTime", map.get("dateTime"));
        safeSet(details, "bank", map.get("bank"));

        return details;
    }

    public static Map<String, Object> parseToMap(String smsBody) {
        if (smsBody == null) return null;
        String s = sanitizeSmsBody(smsBody);

        boolean isReceived = RECEIVED_KEYWORDS.matcher(s).find();
        boolean isSent = SENT_KEYWORDS.matcher(s).find();

        // This is the core logic that prevents non-payment messages from being matched
        // Returns null if only a sent keyword is found, or if no payment keywords are present.
        boolean hasPaymentKeyword = PAYMENT_KEYWORDS.matcher(s).find();

        if (!hasPaymentKeyword) {
            return null;
        }
        if (!isReceived) {
            return null;
        }




        Map<String, Object> out = new HashMap<>();
        out.put("fullSmsBody", smsBody);

        String amountRaw = null;
        BigDecimal amountBig = null;

        Matcher mCtx = AMOUNT_CONTEXT_PATTERN.matcher(s);
        if (mCtx.find()) {
            amountRaw = cleanAmountGroup(mCtx.group(1));
            amountBig = parseAmountToBigDecimal(amountRaw);
        }

        if (amountBig == null) {
            Matcher mAmt = AMOUNT_PATTERN.matcher(s);
            while (mAmt.find()) {
                String cand = cleanAmountGroup(mAmt.group(1));
                if (cand != null && cand.length() > 0) {
                    int start = mAmt.start(1);
                    boolean likelyDate = false;
                    if (start > 0) {
                        char before = s.charAt(start - 1);
                        if (before == '-' || before == '/') likelyDate = true;
                    }
                    if (!likelyDate) {
                        amountRaw = cand;
                        amountBig = parseAmountToBigDecimal(cand);
                        if (amountBig != null) break;
                    }
                }
            }
        }

        out.put("rawAmount", amountRaw);
        out.put("amountBig", amountBig);

        String upiRef = null;
        Matcher mRef = REF_PATTERN.matcher(s);
        if (mRef.find()) {
            upiRef = trimAlphaNum(mRef.group(1));
        }
        if (upiRef == null || upiRef.length() < 4) {
            Matcher mLoose = LOOSE_REF_CANDIDATE.matcher(s);
            while (mLoose.find()) {
                String cand = mLoose.group(1);
                if (cand == null) continue;
                if (cand.matches("^\\d+$")) continue;
                if (amountRaw != null && cand.replace(",", "").equals(amountRaw.replace(",", ""))) continue;
                if (cand.length() >= 6) {
                    upiRef = cand;
                    break;
                }
            }
        }
        out.put("upiRefId", upiRef);

        String vpa = null;
        Matcher mVpa = VPA_PATTERN.matcher(s);
        if (mVpa.find()) {
            vpa = mVpa.group(1).trim();
        }
        out.put("senderVpa", vpa);

        String sender = extractSenderNameBetter(s);
        out.put("senderName", sender);

        String isoDate = extractDateIso(s);
        out.put("dateTime", isoDate);

        String bank = extractBank(s);
        out.put("bank", bank);

        return out;
    }

    private static String sanitizeSmsBody(String s) {
        if (s == null) return null;
        String cleaned = s.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("(?i)(Rs|INR|₹)(?=\\d)", "$1 ");
        return cleaned;
    }

    private static String cleanAmountGroup(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("[^0-9.]", "");
    }

    private static BigDecimal parseAmountToBigDecimal(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replace(",", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimAlphaNum(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("[^A-Za-z0-9\\-]", "");
    }

    private static String extractSenderNameBetter(String s) {
        if (s == null) return null;

        List<Pattern> patterns = Arrays.asList(
                RECEIVED_FROM_PATTERN,
                SENDER_FROM_PATTERN,
                SENDER_BY_PATTERN,
                SENDER_BENEFICIARY_PATTERN,
                CREDITED_TO_PATTERN
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                String cand;
                if (m.groupCount() >= 2 && p == SENDER_BENEFICIARY_PATTERN) {
                    cand = m.group(2);
                } else {
                    cand = m.group(1);
                }
                if (cand == null) continue;
                cand = cand.trim();
                cand = cand.replaceAll("(?i)\\b(on\\s+\\d{1,2}[\\-/]\\d{1,2}[\\-/]\\d{2,4}|UPI\\s*Ref\\b.*|via\\s+[^\\s,]+|with\\s+[^\\s,]+)$", "").trim();
                cand = ACCOUNT_TOKEN_PATTERN.matcher(cand).replaceAll("").trim();
                if (cand.matches("^\\d+$")) continue;
                if (VPA_PATTERN.matcher(cand).find()) continue;
                cand = cand.replaceAll("\\s{2,}", " ").trim();
                if (!cand.isEmpty()) return cand;
            }
        }

        Matcher nearUpi = Pattern.compile("([A-Za-z .]{2,40})\\s+UPI", Pattern.CASE_INSENSITIVE).matcher(s);
        if (nearUpi.find()) {
            String cand = nearUpi.group(1).trim();
            if (!cand.isEmpty() && !VPA_PATTERN.matcher(cand).find()) return cand;
        }

        return null;
    }

    private static String extractDateIso(String s) {
        if (s == null) return null;
        Matcher m = DATE_CANDIDATE.matcher(s);
        while (m.find()) {
            String cand = m.group(1).trim();
            String normalized = cand.replace('/', '-');
            for (DateTimeFormatter fmt : DATE_FORMATTERS) {
                try {
                    LocalDate ld = LocalDate.parse(normalized, fmt);
                    return ld.toString();
                } catch (DateTimeParseException ignored) {
                }
            }
            try {
                DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern("d-M-uu");
                LocalDate ld = LocalDate.parse(normalized, shortFmt);
                return ld.toString();
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static String extractBank(String s) {
        if (s == null) return null;
        Matcher m = BANK_PATTERN.matcher(s);
        if (!m.find()) return null;

        String namePart = null;
        try {
            namePart = m.group(1);
        } catch (Exception ignored) {}
        if (namePart == null) return null;

        namePart = namePart.trim();
        namePart = namePart.replaceAll("(?i)^(in\\s+|to\\s+|your\\s+|our\\s+|my\\s+)", "").trim();
        namePart = ACCOUNT_TOKEN_PATTERN.matcher(namePart).replaceAll("").trim();
        namePart = namePart.replaceAll("[^A-Za-z0-9&.\\s\\-]", "").trim();
        namePart = namePart.replaceAll("\\s{2,}", " ").trim();
        if (namePart.isEmpty()) return null;

        String lower = namePart.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : BANK_NORMALIZATION.entrySet()) {
            if (lower.startsWith(e.getKey())) {
                return e.getValue();
            }
        }

        return capitalizeEachWord(namePart);
    }

    private static String capitalizeEachWord(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] parts = s.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.length() == 0) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private static String cleanBankNameFromMatcher(Matcher m) {
        if (m == null) return null;
        String namePart = "";
        String typePart = "";
        try {
            namePart = m.group(1) != null ? m.group(1).trim() : "";
        } catch (Exception ignored) {}
        try {
            typePart = m.groupCount() >= 2 && m.group(2) != null ? m.group(2).trim() : "";
        } catch (Exception ignored) {}

        if (namePart.isEmpty()) return null;

        namePart = namePart.replaceAll("(?i)^(in\\s+|to\\s+|your\\s+|our\\s+|my\\s+|a\\s+|the\\s+)", "");
        namePart = namePart.replaceAll("[^A-Za-z0-9&.\\s\\-]", "").trim();
        namePart = namePart.replaceAll("\\s{2,}", " ");

        String combined = namePart;
        if (!typePart.isEmpty()) {
            combined = combined + " " + typePart;
        }

        return combined.trim();
    }

    private static PaymentDetails instantiatePaymentDetails() {
        try {
            return PaymentDetails.class.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            try {
                Constructor<PaymentDetails> c = PaymentDetails.class.getDeclaredConstructor();
                c.setAccessible(true);
                return c.newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static void safeSet(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null || value == null) return;

        Class<?> cls = target.getClass();
        String setterName = "set" + capitalize(fieldName);

        try {
            Method[] methods = cls.getMethods();
            for (Method m : methods) {
                if (!m.getName().equalsIgnoreCase(setterName)) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> paramType = m.getParameterTypes()[0];
                Object toPass = convertValueIfNeeded(value, paramType);
                if (toPass != null) {
                    try {
                        m.invoke(target, toPass);
                        return;
                    } catch (Exception ignore) { }
                }
            }
        } catch (SecurityException ignored) {}

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
        } catch (Exception ignored) {}
    }

    private static Object convertValueIfNeeded(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;

        if (targetType == String.class) {
            return value.toString();
        }

        if (targetType == BigDecimal.class) {
            if (value instanceof BigDecimal) return value;
            String s = value.toString().replaceAll("[,]", "");
            try { return new BigDecimal(s); } catch (Exception e) { return null; }
        }

        try {
            String s = value.toString().replaceAll("[,]", "");
            if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(s);
            if (targetType == Long.class || targetType == long.class) return Long.parseLong(s);
            if (targetType == Double.class || targetType == double.class) return Double.parseDouble(s);
            if (targetType == Float.class || targetType == float.class) return Float.parseFloat(s);
            if (targetType == Short.class || targetType == short.class) return Short.parseShort(s);
        } catch (Exception ignored) {}

        return null;
    }

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
}