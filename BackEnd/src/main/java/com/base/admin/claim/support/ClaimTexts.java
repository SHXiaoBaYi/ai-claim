package com.base.admin.claim.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClaimTexts {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern LABELED_PHENOMENON = Pattern.compile("(?:服务|产品|厂库|物流|蔬菜|质量|投诉)-[^，,。；;！!？?\\n]{1,80}");
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
        String digits = text.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() >= 13) {
            digits = digits.substring(digits.length() - 11);
        }
        if (digits.length() >= 11) {
            Matcher compact = PHONE.matcher(digits);
            if (compact.find()) {
                return compact.group();
            }
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

    /** 用户要停掉正在录的投诉。长句里提到取消订单不算中断。 */
    public static boolean isAbortPhrase(String text) {
        String value = empty(text).replaceAll("\\s+", "");
        if (value.isEmpty() || value.length() > 16) {
            return false;
        }
        if (value.contains("新投诉") || value.contains("再录")) {
            return false;
        }
        return value.matches(".*(不投诉了|取消投诉|中断投诉|终止投诉|停止投诉|结束投诉|不录了|先不录|别录了|不用录|不弄了|不做了|不处理了|先算了).*")
                || value.matches("^(取消|中断|终止|停止|结束|算了|不用了|罢了|取消吧|结束吧|停止吧|算了吧)$");
    }

    public static String customerNameOf(String text) {
        if (blank(text)) {
            return "";
        }
        Matcher titled = Pattern.compile("([\\u4e00-\\u9fa5]{1,4}(?:先生|女士|小姐))").matcher(text);
        if (titled.find()) {
            return titled.group(1);
        }
        Matcher labeled = Pattern.compile("(?:姓名|顾客|客户)[:：\\s]*([\\u4e00-\\u9fa5]{2,4}?)(?=反馈|反映|反应|说|投诉|表示|在|于|买|购买|，|,|。|的)").matcher(text);
        return labeled.find() ? labeled.group(1) : "";
    }

    public static String storeNameOf(String text) {
        if (blank(text)) {
            return "";
        }
        for (String store : new String[] {"京东", "天猫", "淘宝", "拼多多", "抖音", "微商城", "微信小店"}) {
            if (text.contains(store)) {
                return store;
            }
        }
        Matcher at = Pattern.compile("在([\\u4e00-\\u9fa5A-Za-z0-9]{1,12}?店)").matcher(text);
        return at.find() ? at.group(1) : "";
    }

    public static String productNameOf(String text) {
        if (blank(text)) {
            return "";
        }
        Matcher bought = Pattern.compile("(?:买的|购买的|买了|购买了)([\\u4e00-\\u9fa5A-Za-z0-9]{1,16}?)(?=太|很|不|有点|有些|发|坏|老|硬|柴|肥|缺|异|烂|臭|膻|腥|开化|变|漏|，|。|$)").matcher(text);
        return bought.find() ? bought.group(1) : "";
    }

    /** 上周按上一个自然周周一到周日。今天 2026-11-11 时，上周是 2026-11-02～2026-11-08。 */
    public static String purchaseDateOf(String text, LocalDate today) {
        if (blank(text) || today == null) {
            return "";
        }
        if (text.contains("上上周")) {
            return weekRange(today, 2);
        }
        if (text.contains("上周") || text.contains("上个星期") || text.contains("上星期")) {
            return weekRange(today, 1);
        }
        if (text.contains("本周") || text.contains("这周") || text.contains("这个星期")) {
            return weekRange(today, 0);
        }
        if (text.contains("上个月")) {
            LocalDate first = today.minusMonths(1).withDayOfMonth(1);
            return first + "～" + first.withDayOfMonth(first.lengthOfMonth());
        }
        if (text.contains("前天")) {
            return today.minusDays(2).toString();
        }
        if (text.contains("昨天")) {
            return today.minusDays(1).toString();
        }
        if (text.contains("今天")) {
            return today.toString();
        }
        return "";
    }

    private static String weekRange(LocalDate today, int weeksAgo) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeksAgo);
        return monday + "～" + monday.plusDays(6);
    }

    public static String clip(String text, int max) {
        String value = empty(text);
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 客诉表按文件年份归年。2026-12-13 在 2025 年文件里记为 2025-12-13。 */
    public static String forceYear(String value, String year) {
        String date = empty(value);
        String target = empty(year);
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}") || !target.matches("\\d{4}") || date.startsWith(target + "-")) {
            return date;
        }
        int month = Integer.parseInt(date.substring(5, 7));
        int day = Integer.parseInt(date.substring(8, 10));
        if (month < 1 || month > 12) {
            return date;
        }
        int maxDay = YearMonth.of(Integer.parseInt(target), month).lengthOfMonth();
        if (day < 1) {
            return date;
        }
        if (day > maxDay) {
            day = maxDay;
        }
        return target + "-" + String.format("%02d-%02d", month, day);
    }

    /** 牛羊肉等肉品。不用单字「果」，避免「坏果」被当成水果。 */
    public static boolean meatText(String text) {
        String value = empty(text);
        return value.contains("羊") || value.contains("牛") || value.contains("肉")
                || value.contains("排骨") || value.contains("牛腩");
    }

    public static boolean produceText(String text) {
        String value = empty(text);
        return value.contains("蔬") || value.contains("水果") || value.contains("果蔬")
                || value.contains("青菜") || value.contains("有机菜") || value.contains("瓜")
                || value.contains("番茄") || value.contains("菌") || value.contains("草莓");
    }

    public static boolean spoilageText(String text) {
        String value = empty(text);
        return value.contains("坏了") || value.contains("变质") || value.contains("腐烂")
                || value.contains("烂了") || value.contains("不新鲜") || value.contains("发臭")
                || value.contains("发黑") || value.contains("肉坏了");
    }

    /** 历史结论是无需理赔或不做处理。这类案例可以对上内容，但不能当作本次处理依据。 */
    public static boolean noClaimOutcome(String... parts) {
        StringBuilder text = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                text.append(empty(part));
            }
        }
        String value = text.toString();
        return value.contains("无需理赔") || value.contains("无需处理") || value.contains("不用处理")
                || value.contains("不做处理") || value.contains("不作处理") || value.contains("不予处理")
                || value.contains("不予理赔") || value.contains("不给予处理") || value.contains("未做处理");
    }

    /**
     * 双向 NLI 等价度。两边都是蕴含时取较小置信度；只有一边蕴含时最高 0.89；任一边矛盾则为 0。
     */
    public static double bidirectionalEquivalence(String forward, double forwardProbability, String backward, double backwardProbability) {
        String forwardLabel = nliLabel(forward);
        String backwardLabel = nliLabel(backward);
        if ("contradiction".equals(forwardLabel) || "contradiction".equals(backwardLabel)) {
            return 0;
        }
        double forwardScore = clampProbability(forwardProbability);
        double backwardScore = clampProbability(backwardProbability);
        if ("entailment".equals(forwardLabel) && "entailment".equals(backwardLabel)) {
            return Math.min(forwardScore, backwardScore);
        }
        if ("entailment".equals(forwardLabel) || "entailment".equals(backwardLabel)) {
            double oneWay = "entailment".equals(forwardLabel) ? forwardScore : backwardScore;
            return Math.min(0.89, oneWay);
        }
        return 0;
    }

    public static String nliLabel(String raw) {
        String value = empty(raw).toLowerCase(java.util.Locale.ROOT);
        if (value.contains("entail") || value.contains("蕴含") || value.equals("e")) {
            return "entailment";
        }
        if (value.contains("contradict") || value.contains("矛盾") || value.equals("c")) {
            return "contradiction";
        }
        return "neutral";
    }

    /** 同一条进度里使用的文本进度条，20 格。 */
    public static String progressBar(String title, int done, int total) {
        int safeTotal = Math.max(total, 1);
        int safeDone = Math.max(0, Math.min(done, safeTotal));
        int percent = (int) Math.round(safeDone * 100.0 / safeTotal);
        int filled = percent * 20 / 100;
        return empty(title) + "\n" + "█".repeat(filled) + "░".repeat(20 - filled) + " " + percent + "%";
    }

    /** 用户原文里的「产品-不香、不好吃」整段，顿号保留，逗号停住。 */
    public static String labeledPhenomenon(String text) {
        if (blank(text)) {
            return "";
        }
        Matcher matcher = LABELED_PHENOMENON.matcher(text);
        StringBuilder found = new StringBuilder();
        while (matcher.find()) {
            String piece = matcher.group().trim();
            if (piece.isEmpty() || found.indexOf(piece) >= 0) {
                continue;
            }
            if (!found.isEmpty()) {
                found.append('、');
            }
            found.append(piece);
        }
        return found.toString();
    }

    /** 去掉分类前缀后现象原文相同。不香 不等于 不香、不好吃。 */
    public static boolean samePhenomenon(String left, String right) {
        String a = phenomenonKey(left);
        String b = phenomenonKey(right);
        return a.length() >= 2 && a.equals(b);
    }

    /** 投诉原文里完整出现了历史现象。短词不靠包含关系算等价。 */
    public static boolean containsPhenomenon(String complaint, String phenomenon) {
        String key = phenomenonKey(phenomenon);
        if (key.length() < 4) {
            return false;
        }
        String haystack = empty(complaint).replaceAll("\\s+", "");
        return haystack.contains(key);
    }

    private static String phenomenonKey(String text) {
        String value = empty(text).replaceAll("\\s+", "");
        int dash = value.indexOf('-');
        if (dash > 0 && dash <= 4 && dash < value.length() - 1) {
            String prefix = value.substring(0, dash);
            if (prefix.matches("服务|产品|厂库|物流|蔬菜|质量|投诉")) {
                value = value.substring(dash + 1);
            }
        }
        return value;
    }

    /** 综合建议固定三段，卡片、落库和人工修改都用这一份。 */
    public static String advice(String nature, String actions, String plan) {
        return "综合建议：\n1. 事件定性：" + empty(nature) + "\n2. 行动要素：" + empty(actions) + "\n3. 具体方案：" + empty(plan);
    }

    private static double clampProbability(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        if (value > 1 && value <= 100) {
            value = value / 100.0;
        }
        return Math.min(1, value);
    }
}
