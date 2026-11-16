package com.base.admin.claim.support;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 《消费者权益保护法》小巴依适用摘录。综合建议先读这份，再对照历史和 SOP。
 */
public final class ClaimConsumerLaw {

    public static final String ROUTING = """
            分流：肉里异物、腐败变质、不合格检疫走《食品安全法》148 条，退一赔十、保底 1000 元；\
            虚标重量、虚假宣传、隐瞒信息等欺诈走《消保法》55 条，退一赔三、保底 500 元；\
            包装漏气、品质和承诺不符但无安全风险走《消保法》23、24 条，退换或补偿，不触发惩罚性赔偿。\
            客诉首接负责，先对接顾客再向上游追责。没有核实前不要承诺惩罚性赔偿。""";

    static final String FALLBACK = """
            《消费者权益保护法》（消协法）逐条适用依据
            食安问题优先适用《食品安全法》。消法侧重经营者义务、宣传、售后、欺诈、退换货、沟通争议。
            第七条（安全权）：牛羊肉、马肠不能有异物、变质、污染；冷链包装和运输要保障食品安全。
            第八条（知情权）：产地、检疫、净重、生产日期、保质期、储存方式、发货时效、是否冷冻、差价规则必须如实告知。
            第二十条（真实宣传）：私域话术、海报、朋友圈不得夸大功效、虚标重量、虚构产地。
            第二十二条（出具票据）：客户索要订单凭证、检疫证明、票据时必须提供。
            第二十三条（质量保证）：收到货的肉质、重量要和宣传一致；漏气、干硬、和描述不符属于本条。
            第二十四条（退换货）：变质、涨袋、规格不符支持售后退换；生鲜有特殊性但质量问题不能拒售后。经营者承担运费。
            第二十六条：不得写「生鲜一经售出概不退换」等霸王条款。
            第四十条：商品造成健康损害，顾客可直接找销售方索赔，销售方先行赔付后再向上游追偿。
            第四十五条：虚假广告误导消费，经营者承担责任。
            第五十五条（欺诈：退一赔三）：欺诈行为退一赔三、保底 500 元。刻意虚标重量、谎称产地、故意隐瞒关键信息、诱导下单适用本条。
            《食品安全法》148 条：肉质变质、异物、不符合食品安全标准，退一赔十、保底 1000 元。
            第五十六条：销售不合格、变质、虚假宣传商品，市场监管可处罚。
            """ + ROUTING;

    private ClaimConsumerLaw() {
    }

    public static String load(Path dir) {
        Path file = find(dir);
        if (file == null) {
            return FALLBACK;
        }
        try {
            String text = readDocx(file);
            return ClaimTexts.blank(text) ? FALLBACK : text;
        } catch (Exception ex) {
            return FALLBACK;
        }
    }

    public static boolean specialClaim(String... texts) {
        String blob = String.join(" ", texts == null ? new String[0] : texts);
        return containsAny(blob, "索赔", "赔偿", "十倍", "赔十", "赔三", "退一赔", "惩罚性",
                "消协", "消保", "消费者权益", "食品安全法", "保底");
    }

    public static Path find(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.contains("消协") && (name.endsWith(".docx") || name.endsWith(".doc"))
                        && !name.startsWith(".~");
            }).findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    static String readDocx(Path file) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return paragraphs(zip.readAllBytes());
                }
            }
        }
        return "";
    }

    private static String paragraphs(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element root;
        try (ByteArrayInputStream in = new ByteArrayInputStream(xml)) {
            root = factory.newDocumentBuilder().parse(in).getDocumentElement();
        }
        StringBuilder text = new StringBuilder();
        NodeList nodes = root.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p");
        if (nodes.getLength() == 0) {
            nodes = root.getElementsByTagName("w:p");
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            StringBuilder line = new StringBuilder();
            appendText(nodes.item(i), line);
            String value = line.toString().trim();
            if (!value.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(value);
            }
        }
        return text.toString();
    }

    private static void appendText(Node node, StringBuilder text) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            Node parent = node.getParentNode();
            if (parent != null && "t".equals(parent.getLocalName())) {
                text.append(node.getNodeValue());
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendText(children.item(i), text);
        }
    }

    private static boolean containsAny(String text, String... words) {
        String value = ClaimTexts.empty(text);
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
