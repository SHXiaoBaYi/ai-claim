package com.base.admin.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 行政通报奖惩细分类型。 */
public final class PayRewardKinds {

    public static final String VERBAL_PRAISE = "口头表扬";
    public static final String COMMENDATION = "通报嘉奖";
    public static final String MERIT = "记功";
    public static final String MAJOR_MERIT = "记大功";
    public static final String VERBAL_CRITIC = "口头批评";
    public static final String BULLETIN_CRITIC = "通报批评";
    public static final String DEMERIT = "记过";
    public static final String MAJOR_DEMERIT = "记大过";

    public static final String[] ALL = {
            VERBAL_PRAISE, COMMENDATION, MERIT, MAJOR_MERIT,
            VERBAL_CRITIC, BULLETIN_CRITIC, DEMERIT, MAJOR_DEMERIT
    };

    private static final Set<String> REWARD = Set.of(VERBAL_PRAISE, COMMENDATION, MERIT, MAJOR_MERIT);
    private static final Set<String> PUNISH = Set.of(VERBAL_CRITIC, BULLETIN_CRITIC, DEMERIT, MAJOR_DEMERIT);

    private PayRewardKinds() {
    }

    public static boolean isReward(String kind) {
        return kind != null && REWARD.contains(kind);
    }

    public static boolean isPunish(String kind) {
        return kind != null && PUNISH.contains(kind);
    }

    public static boolean isKnown(String kind) {
        return isReward(kind) || isPunish(kind);
    }

    /** 从标题/正文识别细分类型；无法识别时返回 null。 */
    public static String classify(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (contains(text, "记大功")) {
            return MAJOR_MERIT;
        }
        if (contains(text, "记功")) {
            return MERIT;
        }
        if (contains(text, "通报嘉奖", "嘉奖")) {
            return COMMENDATION;
        }
        if (contains(text, "口头表扬", "口头奖励", "口头表彰")) {
            return VERBAL_PRAISE;
        }
        if (contains(text, "记大过")) {
            return MAJOR_DEMERIT;
        }
        if (contains(text, "记过")) {
            return DEMERIT;
        }
        if (contains(text, "通报批评")) {
            return BULLETIN_CRITIC;
        }
        if (contains(text, "口头批评", "口头警告")) {
            return VERBAL_CRITIC;
        }
        if (contains(text, "处分", "处罚", "警告", "批评", "惩处", "罚款")) {
            return BULLETIN_CRITIC;
        }
        if (contains(text, "表彰", "奖励", "表扬", "奖")) {
            return COMMENDATION;
        }
        return null;
    }

    public static Map<String, String> columnOf() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(VERBAL_PRAISE, "amt_verbal_praise");
        map.put(COMMENDATION, "amt_commendation");
        map.put(MERIT, "amt_merit");
        map.put(MAJOR_MERIT, "amt_major_merit");
        map.put(VERBAL_CRITIC, "amt_verbal_critic");
        map.put(BULLETIN_CRITIC, "amt_bulletin_critic");
        map.put(DEMERIT, "amt_demerit");
        map.put(MAJOR_DEMERIT, "amt_major_demerit");
        return map;
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
