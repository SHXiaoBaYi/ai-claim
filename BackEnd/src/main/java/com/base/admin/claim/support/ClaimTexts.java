package com.base.admin.claim.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClaimTexts {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ADDRESS = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}(省|自治区).{0,40}?(号|室|楼|栋|街|路|区|县|镇|园|府)");

    private ClaimTexts() {
    }

    public static String empty(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static String phoneOf(String text) {
        if (blank(text)) {
            return "";
        }
        Matcher matcher = PHONE.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    public static String addressOf(String text) {
        if (blank(text)) {
            return "";
        }
        Matcher matcher = ADDRESS.matcher(text.replace("\n", ""));
        String found = matcher.find() ? matcher.group() : "";
        return found.length() > 255 ? found.substring(0, 255) : found;
    }

    public static boolean skipped(String slots, String slot) {
        if (blank(slots) || blank(slot)) {
            return false;
        }
        for (String item : slots.split(",")) {
            if (slot.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String addSkip(String slots, String slot) {
        if (skipped(slots, slot)) {
            return empty(slots);
        }
        return blank(slots) ? slot : slots + "," + slot;
    }

    public static boolean isSkipPhrase(String text) {
        String value = empty(text).replace(" ", "");
        return value.length() <= 12 && value.matches("(没有|无|不清楚|不知道|跳过|暂无|没照片|无法提供|没有照片|暂不清楚).*");
    }

    public static String clip(String text, int max) {
        String value = empty(text);
        return value.length() <= max ? value : value.substring(0, max);
    }
}
