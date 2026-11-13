package com.base.admin.claim.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 投诉现象大类。出建议时先落到这里，再只对照这一类的历史案例和理赔档案。
 */
public final class ClaimSymptomCategory {

    public static final String SMELL = "膻腥异味";
    public static final String FAT = "肥瘦不均";
    public static final String TOUGH = "口感老柴";
    public static final String BLAND = "不香怪味";
    public static final String WEIGHT = "缺斤少两";
    public static final String SPOIL = "变质不新鲜";
    public static final String FOREIGN = "异物";
    public static final String VEG_BAD = "坏果";
    public static final String VEG_CRUSH = "压坏破损";
    public static final String VEG_TASTE = "蔬菜口感";
    public static final String THAW_LIGHT = "轻微开化";
    public static final String THAW_SEVERE = "严重开化";
    public static final String LOST = "丢失超时";
    public static final String PACK = "包装破损";
    public static final String ORIGIN = "发货地不满";
    public static final String SHORT = "漏装少发";
    public static final String WRONG = "发错货";
    public static final String SERVICE = "服务不满";

    public static final String FAMILY_PRODUCT = "产品质量";
    public static final String FAMILY_VEG = "蔬菜质量";
    public static final String FAMILY_LOGISTICS = "物流服务";
    public static final String FAMILY_SERVICE = "服务信息";
    public static final String OTHER = "其他";

    private ClaimSymptomCategory() {
    }

    /** 下拉改过的大类保留。现象原文变了、又没改大类时，按新原文重判。 */
    public static String choose(String submitted, String previous, boolean textChanged, String computed) {
        String chosen = ClaimTexts.empty(submitted);
        if (!chosen.isBlank() && !chosen.equals(ClaimTexts.empty(previous))) {
            return ClaimTexts.clip(chosen, 32);
        }
        if (textChanged || chosen.isBlank()) {
            return computed;
        }
        return ClaimTexts.clip(chosen, 32);
    }

    public static String classify(String phenomenon, String productName, String productCategory, String complaintType, String extra) {
        String head = join(phenomenon, productName, productCategory);
        String hit = specific(head);
        if (hit != null) {
            return hit;
        }
        hit = specific(join(head, extra));
        if (hit != null) {
            return hit;
        }
        return family(join(head, complaintType, extra), phenomenon);
    }

    /** 查询这一类时要用的值。家族名会带上它下面的细类。 */
    public static List<String> searchKeys(String category) {
        String value = ClaimTexts.empty(category);
        if (value.isBlank()) {
            return List.of(OTHER);
        }
        if (isFamily(value)) {
            return bucket(value);
        }
        return List.of(value);
    }

    /** 细类里没有记录时，扩大到同一家族。家族本身和「其他」不再扩大。 */
    public static List<String> widenKeys(String category) {
        String family = familyOf(ClaimTexts.empty(category));
        if (family.isBlank() || family.equals(ClaimTexts.empty(category)) || OTHER.equals(family)) {
            return List.of();
        }
        return bucket(family);
    }

    public static String familyOf(String category) {
        String value = ClaimTexts.empty(category);
        return switch (value) {
            case SMELL, FAT, TOUGH, BLAND, WEIGHT, SPOIL, FOREIGN, FAMILY_PRODUCT -> FAMILY_PRODUCT;
            case VEG_BAD, VEG_CRUSH, VEG_TASTE, FAMILY_VEG -> FAMILY_VEG;
            case THAW_LIGHT, THAW_SEVERE, LOST, PACK, FAMILY_LOGISTICS -> FAMILY_LOGISTICS;
            case ORIGIN, SHORT, WRONG, SERVICE, FAMILY_SERVICE -> FAMILY_SERVICE;
            case OTHER -> OTHER;
            default -> "";
        };
    }

    public static boolean isFamily(String category) {
        return FAMILY_PRODUCT.equals(category) || FAMILY_VEG.equals(category)
                || FAMILY_LOGISTICS.equals(category) || FAMILY_SERVICE.equals(category);
    }

    private static List<String> bucket(String family) {
        List<String> keys = new ArrayList<>();
        keys.add(family);
        switch (family) {
            case FAMILY_PRODUCT -> keys.addAll(List.of(SMELL, FAT, TOUGH, BLAND, WEIGHT, SPOIL, FOREIGN));
            case FAMILY_VEG -> keys.addAll(List.of(VEG_BAD, VEG_CRUSH, VEG_TASTE));
            case FAMILY_LOGISTICS -> keys.addAll(List.of(THAW_LIGHT, THAW_SEVERE, LOST, PACK));
            case FAMILY_SERVICE -> keys.addAll(List.of(ORIGIN, SHORT, WRONG, SERVICE));
            default -> {
            }
        }
        return keys;
    }

    private static String specific(String text) {
        String value = ClaimTexts.empty(text);
        if (value.isBlank()) {
            return null;
        }
        if (value.contains("开化")) {
            if (containsAny(value, "异味", "臭", "黏", "粘", "血水", "严重", "变质", "发臭", "坏了", "腐烂", "温热")) {
                return THAW_SEVERE;
            }
            return THAW_LIGHT;
        }
        if (containsAny(value, "时效", "超时", "未按时间", "没收到", "未收到", "丢件", "未送达", "未送到", "滞留", "没送到")) {
            return LOST;
        }
        if (containsAny(value, "漏气", "包装破损", "真空袋", "破损", "外箱")) {
            if (produce(value) && !meat(value)) {
                return VEG_CRUSH;
            }
            return PACK;
        }
        if (containsAny(value, "发错货", "发错")) {
            return WRONG;
        }
        if (containsAny(value, "漏发短信", "服务不满", "态度差", "态度不好", "态度恶劣", "宣传不符", "实物不符")) {
            return SERVICE;
        }
        if (containsAny(value, "漏装", "少发", "少送", "漏货", "少配", "货品不够", "漏发货")
                || (value.contains("漏发") && !value.contains("短信"))) {
            return SHORT;
        }
        if (containsAny(value, "发货地", "不是新疆", "发货仓", "中转仓")) {
            return ORIGIN;
        }
        if (containsAny(value, "异物", "头发", "塑料", "虫子", "石子")) {
            return FOREIGN;
        }
        if (containsAny(value, "缺斤", "少两", "重量不足", "不够秤", "分量不够", "量太少", "短斤")) {
            return WEIGHT;
        }
        if (containsAny(value, "膻", "腥", "异味")) {
            return SMELL;
        }
        if (containsAny(value, "太肥", "肥瘦", "油大", "油太多", "油太大")) {
            return FAT;
        }
        if (containsAny(value, "临期", "过期", "涨袋", "日期太久")) {
            return SPOIL;
        }
        if (produce(value) && !meat(value) && containsAny(value, "坏", "烂", "不新鲜", "发软", "变质", "发黑", "腐烂")) {
            return VEG_BAD;
        }
        if (containsAny(value, "不新鲜", "变质", "发臭", "发黑", "腐烂", "坏了")) {
            return SPOIL;
        }
        if (containsAny(value, "不香", "没味", "怪味", "味道淡")) {
            return produce(value) && !meat(value) ? VEG_TASTE : BLAND;
        }
        if (tough(value)) {
            return produce(value) && !meat(value) ? VEG_TASTE : TOUGH;
        }
        return null;
    }

    private static boolean tough(String value) {
        if (containsAny(value, "柴", "嚼不动", "咬不动", "太硬", "很硬", "不好吃")) {
            return true;
        }
        String stripped = value.replace("老版", "").replace("老顾客", "").replace("老客", "").replace("老板", "");
        return stripped.contains("老") || stripped.contains("硬");
    }

    private static String family(String text, String phenomenon) {
        String value = ClaimTexts.empty(text);
        String prefix = prefix(ClaimTexts.empty(phenomenon));
        if (containsAny(prefix, "物流", "顺丰", "绿色通道") || value.contains("物流投诉")) {
            return FAMILY_LOGISTICS;
        }
        if (prefix.startsWith("服务") || value.contains("服务投诉")) {
            return FAMILY_SERVICE;
        }
        if (produce(value) && !meat(value)) {
            return FAMILY_VEG;
        }
        if (meat(value) || value.contains("产品投诉") || prefix.startsWith("产品")) {
            return FAMILY_PRODUCT;
        }
        return OTHER;
    }

    private static String prefix(String text) {
        int dash = text.indexOf('-');
        if (dash <= 0 || dash > 8) {
            int full = text.indexOf('—');
            dash = full > 0 && full <= 8 ? full : -1;
        }
        return dash > 0 ? text.substring(0, dash) : "";
    }

    private static boolean meat(String text) {
        return ClaimTexts.meatText(text) || containsAny(text, "鸡", "鸽", "马肉", "马肠", "鸭");
    }

    private static boolean produce(String text) {
        return ClaimTexts.produceText(text) || containsAny(text,
                "黄瓜", "西红柿", "白菜", "包包菜", "菜心", "生菜", "茼蒿", "豆角", "胡萝卜",
                "青菜", "椒", "萝卜", "土豆", "洋葱", "芹菜", "菠菜", "西兰花", "花菜", "玉米", "茄子", "蘑菇", "木耳", "香菜");
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
