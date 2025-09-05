package com.example.paymenttracker;

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
            "(?i)\\b(?:credited|credit|credit\\s+of|cr:?|cr\\b|rcvd|received|deposit|refund(?:ed)?|revers(?:ed|al)?)\\b"
    );

    private static final Pattern SENT_KEYWORDS = Pattern.compile(
            "(?i)\\b(?:debited|debit|dr:?|dr\\b|sent|paid|transfer(?:red)?|withdrawn|withdraw)\\b"
    );

    // amounts
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "(?i)(?:Rs\\.?|INR|₹)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?)"
    );
    private static final Pattern AMOUNT_CONTEXT_PATTERN = Pattern.compile(
            "(?i)(?:credited|debited|paid|txn for|transferred|received|amount)\\s*(?:of|by|with|:)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?)"
    );
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?)"
    );

    // explicit refs
    private static final Pattern REF_PATTERN = Pattern.compile(
            "(?i)(?:UPI\\s*Ref(?:\\s*No)?|Ref\\.No|Ref\\s*No|Ref\\b|Txn ID|TXN ID|TxN|TXN|TRANS ID|Transaction ID)[:\\s-]*([A-Za-z0-9\\-]{4,40})"
    );
    // loose candidates must contain at least one digit; we'll filter more later
    private static final Pattern LOOSE_REF_CANDIDATE = Pattern.compile(
            "\\b(?=[A-Za-z0-9-]*\\d)([A-Za-z0-9-]{6,40})\\b"
    );

    private static final Pattern VPA_PATTERN = Pattern.compile(
            "\\b([A-Za-z0-9._%+\\-]+@[A-Za-z0-9.-]+)\\b", Pattern.CASE_INSENSITIVE
    );

    // date detection
    private static final Pattern DATE_CANDIDATE = Pattern.compile(
            "\\b(\\d{1,2}[-/][A-Za-z]{3}[-/]\\d{4}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})\\b"
    );

    // bank extraction
    private static final Pattern BANK_PATTERN_WITH_PHRASE = Pattern.compile(
            "(?i)\\b(?:with|in your|in|your|account of)\\s+([A-Za-z&.\\- ]{1,40}?)\\s+Bank\\b"
    );
    private static final Pattern BANK_PATTERN_START = Pattern.compile(
            "(?i)^([A-Za-z&.\\- ]{1,40}?)\\s+Bank:?\\b"
    );
    private static final Pattern BANK_PATTERN_GENERAL = Pattern.compile(
            "(?i)\\b([A-Za-z&.\\- ]{1,40}?)\\s+Bank\\b"
    );

    private static final Pattern ACCOUNT_TOKEN_PATTERN = Pattern.compile(
            "(?i)(?:\\bX?\\*{0,4}\\d{2,}\\b|\\bAC\\b|\\bA/c\\b|\\bAcct\\b|\\bAccount\\b|\\bending\\b|\\blast\\b|\\bending\\s*\\d{1,4}\\b|\\b\\d{3,}\\b)"
    );

    private static final Set<String> WORD_BLACKLIST = new HashSet<>(Arrays.asList(
            "received", "credited", "credit", "payment", "amount", "ref", "transaction", "customer", "upi", "avl", "avl bal", "account", "in", "your", "to", "from", "via", "on"
    ));

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

    public static PaymentDetails parse(String smsBody) {
        if (smsBody == null || smsBody.trim().isEmpty()) {
            return null;
        }

        Map<String, Object> map = parseToMap(smsBody);
        if (map == null) {
            return null;
        }

        Object amountVal = map.get("amountNumber");
        String amountRaw = (String) map.get("amountRaw");
        String ref = (String) map.get("upiRef");
        String vpa = (String) map.get("vpa");
        String bank = (String) map.get("bank");

        boolean hasAmount = amountVal != null || (amountRaw != null && !amountRaw.isEmpty());
        boolean hasRefOrVpaOrBank = (ref != null && !ref.isEmpty()) || (vpa != null && !vpa.isEmpty()) || (bank != null && !bank.isEmpty());

        if (!hasAmount || !hasRefOrVpaOrBank) {
            return null;
        }

        PaymentDetails details = instantiatePaymentDetails();
        if (details == null) {
            return null;
        }

        safeSet(details, "amount", amountVal);
        safeSet(details, "rawAmount", amountRaw);
        safeSet(details, "upiRefId", ref);
        safeSet(details, "senderVpa", vpa);
        safeSet(details, "bank", bank);
        safeSet(details, "dateTime", map.get("dateCandidate"));

        return details;
    }

    public static Map<String, Object> parseToMap(String smsBody) {
        if (smsBody == null) return null;
        String s = sanitizeSmsBody(smsBody);
        s = sanitizeForGatekeeper(s);

        boolean isReceived = RECEIVED_KEYWORDS.matcher(s).find();
        boolean isSent = SENT_KEYWORDS.matcher(s).find();
        boolean hasPaymentKeyword = PAYMENT_KEYWORDS.matcher(s).find();

        if (!hasPaymentKeyword) {
            return null;
        }

        if (!isReceived ) {
            return null;
        }

        Map<String, Object> out = new HashMap<>();
        out.put("fullSmsBody", smsBody);
        out.put("isReceived", isReceived);

        String amountRaw = null;
        BigDecimal amountNumber = null;

        Matcher m = CURRENCY_AMOUNT.matcher(s);
        if (m.find()) {
            amountRaw = cleanAmountGroup(m.group(1));
            amountNumber = parseAmountToBigDecimal(amountRaw);
        }

        if (amountNumber == null) {
            Matcher mCtx = AMOUNT_CONTEXT_PATTERN.matcher(s);
            if (mCtx.find()) {
                amountRaw = cleanAmountGroup(mCtx.group(1));
                amountNumber = parseAmountToBigDecimal(amountRaw);
            }
        }

        if (amountNumber == null) {
            Matcher mAmt = AMOUNT_PATTERN.matcher(s);
            while (mAmt.find()) {
                String cand = cleanAmountGroup(mAmt.group(1));
                if (cand != null && cand.length() > 0) {
                    String onlyDigits = cand.replaceAll("\\D", "");
                    if (onlyDigits.length() >= 11) continue;
                    amountRaw = cand;
                    amountNumber = parseAmountToBigDecimal(cand);
                    if (amountNumber != null) break;
                }
            }
        }

        out.put("amountRaw", amountRaw);
        out.put("amountNumber", amountNumber);

        String upiRef = extractRef(s);
        out.put("upiRef", upiRef);

        String vpa = extractVpa(s);
        out.put("vpa", vpa);

        String bank = extractBank(s);
        out.put("bank", bank);

        String dateCandidate = extractDateIso(s);
        out.put("dateCandidate", dateCandidate);

        return out;
    }

    // --- Helpers from JS code, adapted for Java ---

    private static String sanitizeSmsBody(String s) {
        if (s == null) return null;
        return s.replace("\r", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
    }

    private static String sanitizeForGatekeeper(String s) {
        return s.replaceAll("(?i)\\bprapt(?:\\s+hue|\\s+huye)?\\b", "received");
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

    private static boolean looksLikeAccountMask(String s) {
        if (s == null) return false;
        if (s.matches("(?i)^(X+|x+|\\*+)[0-9]+$")) return true;
        if (s.matches("(?i)^[A-Za-z]{1,3}\\d{2,6}$") && s.matches("(?i).*[Xx\\*].*")) return true;
        if (s.matches("(?i)^XX\\d{2,6}$")) return true;
        return false;
    }

    private static String extractRef(String s) {
        if (s == null) return null;

        // 1. Check for explicit labelled reference number first.
        Matcher m = REF_PATTERN.matcher(s);
        if (m.find()) {
            if (m.groupCount() >= 1 && m.group(1) != null) {
                String cand = m.group(1).replaceAll("[).,;:]+$", "");
                if (!looksLikeAccountMask(cand)) {
                    // Found a good explicit match, return it immediately.
                    return cand;
                }
            }
        }

        // 2. Fallback to loose candidates only if no explicit match was found.
        Matcher mLoose = LOOSE_REF_CANDIDATE.matcher(s);
        while (mLoose.find()) {
            String cand = mLoose.group(1);
            if (cand == null) continue;
            String lc = cand.toLowerCase(Locale.ROOT);
            if (WORD_BLACKLIST.contains(lc)) continue;
            if (looksLikeAccountMask(cand)) continue;
            if (cand.matches("(?i)^[A-Za-z]+$")) continue;
            if (cand.length() < 6) continue;
            String digitsOnly = cand.replaceAll("\\D", "");
            if (digitsOnly.length() >= 11) continue;
            return cand;
        }
        return null;
    }

    private static String extractVpa(String s) {
        if (s == null) return null;
        Matcher m = VPA_PATTERN.matcher(s);
        if (m.find() && m.group(1) != null) {
            return m.group(1).replaceAll("[).,;:]+$", "");
        }
        return null;
    }

    private static String extractBank(String s) {
        if (s == null) return null;

        String cleanSms = s.toLowerCase(Locale.ROOT);

        // Iterate through the normalization map to find a match
        for (Map.Entry<String, String> entry : BANK_NORMALIZATION.entrySet()) {
            String keyword = entry.getKey();
            String normalizedName = entry.getValue();
            if (cleanSms.contains(keyword)) {
                return normalizedName;
            }
        }

        return null;
    }

    private static String extractDateIso(String s) {
        if (s == null) return null;
        Matcher m = DATE_CANDIDATE.matcher(s);
        if (!m.find()) return null;
        String cand = m.group(1);
        String mText;
        try {
            mText = "(?i)^(\\d{1,2})[-/]([A-Za-z]{3})[-/](\\d{4})$";
            Pattern pText = Pattern.compile(mText);
            Matcher mTextMatcher = pText.matcher(cand);
            if (mTextMatcher.matches()) {
                String d = String.format("%02d", Integer.parseInt(mTextMatcher.group(1)));
                String mon = mTextMatcher.group(2).toLowerCase().substring(0, 3);
                Map<String, String> months = new HashMap<>();
                months.put("jan", "01"); months.put("feb", "02"); months.put("mar", "03");
                months.put("apr", "04"); months.put("may", "05"); months.put("jun", "06");
                months.put("jul", "07"); months.put("aug", "08"); months.put("sep", "09");
                months.put("oct", "10"); months.put("nov", "11"); months.put("dec", "12");
                if (months.containsKey(mon)) {
                    return mTextMatcher.group(3) + "-" + months.get(mon) + "-" + d;
                }
            }
        } catch (Exception ignored) {}

        String mNums;
        try {
            mNums = "^(\\d{1,4})[-/](\\d{1,2})[-/](\\d{1,4})$";
            Pattern pNums = Pattern.compile(mNums);
            Matcher mNumsMatcher = pNums.matcher(cand);
            if (mNumsMatcher.matches()) {
                if (mNumsMatcher.group(1).length() == 4) {
                    String yyyy = String.format("%04d", Integer.parseInt(mNumsMatcher.group(1)));
                    String mm = String.format("%02d", Integer.parseInt(mNumsMatcher.group(2)));
                    String dd = String.format("%02d", Integer.parseInt(mNumsMatcher.group(3)));
                    return yyyy + "-" + mm + "-" + dd;
                } else {
                    String dd = String.format("%02d", Integer.parseInt(mNumsMatcher.group(1)));
                    String mm = String.format("%02d", Integer.parseInt(mNumsMatcher.group(2)));
                    String yyyy = mNumsMatcher.group(3);
                    if (yyyy.length() == 2) yyyy = "20" + yyyy;
                    return yyyy + "-" + mm + "-" + dd;
                }
            }
        } catch (Exception ignored) {}

        return null;
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