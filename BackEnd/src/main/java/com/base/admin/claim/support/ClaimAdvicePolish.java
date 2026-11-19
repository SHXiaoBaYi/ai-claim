package com.base.admin.claim.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 综合建议出稿后的三条口径。历史案例和 SOP 里的相反写法，以这里为准。
 * 样品只在无法判定的异物时取回；行动步骤必须核物流时效、轨迹和收货远近；参考话术不写退款。
 */
public final class ClaimAdvicePolish {

    static final String LOGISTICS_STEP = "核对物流时效和物流轨迹，并结合收货地址远近综合研判。";
    static final String RETRIEVAL_STEP = "该异物无法直接判定，安排取回样品核验。";
    static final String SCRIPT_FALLBACK = "您好，非常抱歉给您带来不便。我们会按核实结果给您补发或补偿，请您稍等。";

    private static final Pattern NEXT_SECTION = Pattern.compile("\\n\\s*\\d+\\.\\s*");
    private static final Pattern RETURN_CLAUSE = Pattern.compile(
            "[，,、；;]?[^，,。；;！!？?\\n]{0,40}?(?:样品|货品|原件|实物)?(?:寄回|取回)(?:样品|货品|公司)?[^，,。；;！!？?\\n]{0,20}");

    private ClaimAdvicePolish() {
    }

    public static String apply(String complaint, String advice) {
        String text = ClaimTexts.empty(advice);
        if (text.isBlank()) {
            return text;
        }
        text = tuneSampleRetrieval(ClaimTexts.empty(complaint), text);
        text = ensureLogisticsCheck(text);
        return stripRefundScript(text);
    }

    private static String tuneSampleRetrieval(String complaint, String advice) {
        if (unknownForeign(complaint)) {
            return ensureRetrieval(advice);
        }
        if (keepsProductReturn(complaint)) {
            return advice;
        }
        if (knownForeign(complaint) || odorIssue(complaint) || complaint.contains("异物")) {
            return dropRetrieval(advice);
        }
        return advice;
    }

    /** 动物芯片、疑似玻璃这类无法直接判定的异物，才安排取回。 */
    static boolean unknownForeign(String complaint) {
        return contains(complaint, "芯片", "玻璃", "无法判定", "无法判断", "无法识别", "看不清", "不明异物", "不明物", "可疑异物", "疑似异物");
    }

    /** 头发、花椒等一眼能判定的异物，不取回。 */
    static boolean knownForeign(String complaint) {
        if (unknownForeign(complaint)) {
            return false;
        }
        return contains(complaint, "头发", "毛发", "花椒", "塑料", "虫子", "石子", "石头", "线头", "纸屑");
    }

    static boolean odorIssue(String complaint) {
        if (unknownForeign(complaint)) {
            return false;
        }
        return contains(complaint, "异味", "膻味", "膻", "腥味", "怪味", "臭味", "发臭", "有臭");
    }

    /** 缺斤、薅羊毛、发货地、人为做假，仍按原流程取回货品，不套异物样品规则。 */
    private static boolean keepsProductReturn(String complaint) {
        return contains(complaint, "缺斤", "少两", "不够秤", "重量不足", "分量不够", "薅", "发货地", "不是新疆", "做假", "不属实");
    }

    private static String ensureRetrieval(String advice) {
        String actions = section(advice, "行动步骤");
        if (actions.contains("取回") || actions.contains("寄回")) {
            return advice;
        }
        return replaceSection(advice, "行动步骤", join(actions, RETRIEVAL_STEP));
    }

    private static String dropRetrieval(String advice) {
        String text = advice;
        text = replaceSection(text, "行动步骤", blankAs(stripReturn(section(text, "行动步骤")), "核对照片和客群后再定方案。"));
        text = replaceSection(text, "具体方案", blankAs(stripReturn(section(text, "具体方案")), "按核实结果补发或补偿。"));
        String script = stripReturn(section(text, "给顾客的参考话术"));
        if (!script.isBlank()) {
            text = replaceSection(text, "给顾客的参考话术", script);
        }
        return text;
    }

    private static String stripReturn(String section) {
        if (section.isBlank()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String sentence : section.split("(?<=[。！？\\n])")) {
            if (sentence.isBlank()) {
                continue;
            }
            if (!mentionsReturn(sentence) || negatesReturn(sentence)) {
                kept.append(sentence);
                continue;
            }
            String rest = RETURN_CLAUSE.matcher(sentence).replaceAll("");
            rest = rest.replaceAll("^[，,、；;\\s]+", "")
                    .replaceAll("[，,]{2,}", "，")
                    .replaceAll("，([。！？])", "$1")
                    .trim();
            if (rest.isBlank() || rest.matches("^[。！？\\s]+$")) {
                continue;
            }
            kept.append(rest);
        }
        return kept.toString().trim();
    }

    private static boolean mentionsReturn(String sentence) {
        return sentence.contains("取回") || sentence.contains("寄回");
    }

    private static boolean negatesReturn(String sentence) {
        return contains(sentence, "不必", "无需", "不用", "不要", "不需要", "不安排", "均不", "不取回", "不寄回");
    }

    private static String ensureLogisticsCheck(String advice) {
        String actions = section(advice, "行动步骤");
        if (!advice.contains("行动步骤")) {
            return advice;
        }
        boolean time = actions.contains("时效");
        boolean track = actions.contains("轨迹");
        boolean distance = actions.contains("远近") || actions.contains("收货地址");
        if (time && track && distance) {
            return advice;
        }
        String extra;
        if (time && track) {
            extra = "并结合收货地址远近综合研判。";
        } else if (time) {
            extra = "核对物流轨迹，并结合收货地址远近综合研判。";
        } else if (track) {
            extra = "核对物流时效，并结合收货地址远近综合研判。";
        } else {
            extra = LOGISTICS_STEP;
        }
        return replaceSection(advice, "行动步骤", join(actions, extra));
    }

    private static String stripRefundScript(String advice) {
        if (!advice.contains("给顾客的参考话术")) {
            return advice;
        }
        String cleaned = sanitizeScript(section(advice, "给顾客的参考话术"));
        if (cleaned.isBlank()) {
            cleaned = SCRIPT_FALLBACK;
        }
        return replaceSection(advice, "给顾客的参考话术", cleaned);
    }

    static String sanitizeScript(String script) {
        String text = ClaimTexts.empty(script)
                .replace("退货退款", "补发")
                .replace("全额退款", "补发")
                .replace("原单退款", "补发")
                .replaceAll("退款(或者|或|、)(补发|换货|补偿|赔偿)", "$2")
                .replaceAll("(补发|换货|补偿|赔偿)(或者|或|、)退款", "$1")
                .replace("给您退款", "给您补发")
                .replace("为您退款", "为您补发")
                .replace("帮您退款", "帮您补发")
                .replace("办理退款", "安排补发")
                .replace("申请退款", "安排补发")
                .replace("做退款", "做补发")
                .replace("退款处理", "补发")
                .replace("退换货", "补发")
                .replace("退换", "补发");
        StringBuilder kept = new StringBuilder();
        for (String part : text.split("(?<=[。！？\\n])")) {
            if (part.contains("退款") || part.contains("退货") || part.contains("退钱")) {
                continue;
            }
            kept.append(part);
        }
        return kept.toString()
                .replaceAll("[，,]{2,}", "，")
                .replaceAll("^[，,、；;\\s]+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String join(String existing, String extra) {
        String text = ClaimTexts.empty(existing);
        if (text.isBlank()) {
            return extra;
        }
        if (!text.endsWith("。") && !text.endsWith("！") && !text.endsWith("？")) {
            text = text + "。";
        }
        return text + extra;
    }

    private static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String section(String advice, String label) {
        String marker = label + "：";
        int start = advice.indexOf(marker);
        if (start < 0) {
            marker = label + ":";
            start = advice.indexOf(marker);
        }
        if (start < 0) {
            return "";
        }
        int body = start + marker.length();
        int next = advice.length();
        Matcher matcher = NEXT_SECTION.matcher(advice);
        if (matcher.find(body)) {
            next = matcher.start();
        }
        return advice.substring(body, next).trim();
    }

    private static String replaceSection(String advice, String label, String body) {
        String marker = label + "：";
        int start = advice.indexOf(marker);
        if (start < 0) {
            marker = label + ":";
            start = advice.indexOf(marker);
        }
        if (start < 0) {
            return advice;
        }
        int bodyAt = start + marker.length();
        int next = advice.length();
        Matcher matcher = NEXT_SECTION.matcher(advice);
        if (matcher.find(bodyAt)) {
            next = matcher.start();
        }
        return advice.substring(0, bodyAt) + ClaimTexts.empty(body) + advice.substring(next);
    }

    private static boolean contains(String text, String... words) {
        String value = ClaimTexts.empty(text);
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
