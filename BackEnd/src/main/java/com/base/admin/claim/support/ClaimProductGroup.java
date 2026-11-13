package com.base.admin.claim.support;

/**
 * 现象大类下面的产品分组。出建议时先对上大类，再只看同一产品。
 */
public final class ClaimProductGroup {

    public static final String LAMB = "羊肉";
    public static final String BEEF = "牛肉";
    public static final String POULTRY = "禽类";
    public static final String PRODUCE = "蔬菜";
    public static final String PREPARED = "熟食";
    public static final String OTHER = "其他";

    private ClaimProductGroup() {
    }

    public static String of(String productName, String productCategory, String phenomenon) {
        String prefix = prefix(phenomenon);
        String text = join(productName, productCategory, useful(prefix) ? prefix : "");
        if (text.isBlank()) {
            return OTHER;
        }
        if (prepared(text)) {
            return PREPARED;
        }
        boolean sheep = text.contains("羊");
        boolean cow = text.contains("牛");
        boolean bird = containsAny(text, "鸡", "鸽", "鸭");
        boolean veg = produce(text);
        if (bird && !sheep && !cow) {
            return POULTRY;
        }
        if (veg && !sheep && !cow) {
            return PRODUCE;
        }
        if (sheep && !cow) {
            return LAMB;
        }
        if (cow && !sheep) {
            return BEEF;
        }
        return OTHER;
    }

    private static boolean prepared(String text) {
        return containsAny(text, "火锅", "肠", "饼", "卤", "包子", "熏", "熟食", "腌制");
    }

    private static boolean produce(String text) {
        return ClaimTexts.produceText(text) || containsAny(text,
                "黄瓜", "西红柿", "白菜", "包包菜", "菜心", "生菜", "茼蒿", "豆角", "胡萝卜",
                "青菜", "椒", "萝卜", "土豆", "洋葱", "芹菜", "菠菜", "西兰花", "花菜", "玉米", "茄子", "蘑菇", "木耳", "香菜");
    }

    private static boolean useful(String prefix) {
        return !prefix.isBlank() && !containsAny(prefix, "物流", "服务", "产品", "顺丰", "绿色通道", "其他");
    }

    private static String prefix(String text) {
        String value = ClaimTexts.empty(text);
        int dash = value.indexOf('-');
        if (dash <= 0 || dash > 8) {
            int full = value.indexOf('—');
            dash = full > 0 && full <= 8 ? full : -1;
        }
        return dash > 0 ? value.substring(0, dash) : "";
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String join(String... parts) {
        StringBuilder text = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                if (!ClaimTexts.blank(part)) {
                    text.append(part);
                }
            }
        }
        return text.toString();
    }
}
