package com.base.admin.claim.support;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 读取客诉 xlsx 的第一个含「投诉日期」表头的工作表，或按表名读取。
 */
public final class ClaimHistoryExcel {

    public record SheetTable(String name, List<Map<String, String>> rows) {
    }

    private ClaimHistoryExcel() {
    }

    public static List<SheetTable> read(Path file) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        List<String> shared = readSharedStrings(entries.get("xl/sharedStrings.xml"));
        List<SheetRef> sheets = readWorkbook(entries);
        List<SheetTable> tables = new ArrayList<>();
        for (SheetRef sheet : sheets) {
            byte[] xml = entries.get(sheet.path());
            if (xml == null) {
                continue;
            }
            tables.add(new SheetTable(sheet.name(), toRecords(readRows(xml, shared))));
        }
        return tables;
    }

    private record SheetRef(String name, String path) {
    }

    private static List<SheetRef> readWorkbook(Map<String, byte[]> entries) {
        Element workbook = parse(entries.get("xl/workbook.xml"));
        Element rels = parse(entries.get("xl/_rels/workbook.xml.rels"));
        Map<String, String> targets = new LinkedHashMap<>();
        if (rels != null) {
            NodeList list = rels.getElementsByTagName("Relationship");
            for (int i = 0; i < list.getLength(); i++) {
                Element rel = (Element) list.item(i);
                String target = rel.getAttribute("Target");
                if (!target.startsWith("xl/")) {
                    target = "xl/" + target.replaceFirst("^/", "");
                }
                targets.put(rel.getAttribute("Id"), target);
            }
        }
        List<SheetRef> sheets = new ArrayList<>();
        if (workbook == null) {
            return sheets;
        }
        NodeList list = workbook.getElementsByTagName("sheet");
        for (int i = 0; i < list.getLength(); i++) {
            Element sheet = (Element) list.item(i);
            String rid = sheet.getAttribute("r:id");
            if (rid.isBlank()) {
                rid = sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            }
            String path = targets.get(rid);
            if (path != null) {
                sheets.add(new SheetRef(sheet.getAttribute("name"), path));
            }
        }
        return sheets;
    }

    private static List<String> readSharedStrings(byte[] xml) {
        List<String> strings = new ArrayList<>();
        Element root = parse(xml);
        if (root == null) {
            return strings;
        }
        NodeList sis = root.getElementsByTagName("si");
        for (int i = 0; i < sis.getLength(); i++) {
            StringBuilder text = new StringBuilder();
            appendText(sis.item(i), text);
            strings.add(text.toString());
        }
        return strings;
    }

    private static void appendText(Node node, StringBuilder text) {
        if (node.getNodeType() == Node.TEXT_NODE && "t".equals(node.getParentNode().getLocalName())) {
            text.append(node.getNodeValue());
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendText(children.item(i), text);
        }
    }

    private static List<Map<String, String>> readRows(byte[] xml, List<String> shared) {
        List<Map<String, String>> rows = new ArrayList<>();
        Element root = parse(xml);
        if (root == null) {
            return rows;
        }
        NodeList rowNodes = root.getElementsByTagName("row");
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Map<String, String> row = new LinkedHashMap<>();
            NodeList cells = ((Element) rowNodes.item(i)).getElementsByTagName("c");
            for (int c = 0; c < cells.getLength(); c++) {
                Element cell = (Element) cells.item(c);
                String ref = cell.getAttribute("r");
                String col = ref.replaceAll("\\d", "");
                String value = cellValue(cell, shared);
                if (!col.isBlank() && !value.isBlank()) {
                    row.put(col, normalizeDate(value));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static String cellValue(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            StringBuilder text = new StringBuilder();
            appendText(cell, text);
            return text.toString().trim();
        }
        NodeList values = cell.getElementsByTagName("v");
        if (values.getLength() == 0 || values.item(0).getTextContent() == null) {
            return "";
        }
        String raw = values.item(0).getTextContent().trim();
        if ("s".equals(type)) {
            int index = Integer.parseInt(raw);
            return index >= 0 && index < shared.size() ? shared.get(index).trim() : "";
        }
        if (raw.endsWith(".0")) {
            raw = raw.substring(0, raw.length() - 2);
        }
        return raw;
    }

    private static List<Map<String, String>> toRecords(List<Map<String, String>> rows) {
        int headerAt = -1;
        Map<String, String> header = null;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).containsValue("投诉日期")) {
                headerAt = i;
                header = rows.get(i);
                break;
            }
        }
        List<Map<String, String>> records = new ArrayList<>();
        if (header == null) {
            return records;
        }
        Map<String, String> colToName = new LinkedHashMap<>();
        header.forEach((col, name) -> colToName.put(col, name.replace("\n", "").trim()));
        for (int i = headerAt + 1; i < rows.size(); i++) {
            Map<String, String> record = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : rows.get(i).entrySet()) {
                String name = colToName.get(entry.getKey());
                if (name != null && !name.isBlank()) {
                    record.put(name, entry.getValue());
                }
            }
            if (record.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
                records.add(record);
            }
        }
        return records;
    }

    static String normalizeDate(String raw) {
        if (raw != null && raw.matches("\\d{5}")) {
            int serial = Integer.parseInt(raw);
            if (serial >= 40000 && serial <= 60000) {
                return LocalDate.of(1899, 12, 30).plusDays(serial).toString();
            }
        }
        return raw == null ? "" : raw;
    }

    private static Element parse(byte[] xml) {
        if (xml == null || xml.length == 0) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            try (InputStream in = new ByteArrayInputStream(xml)) {
                return factory.newDocumentBuilder().parse(in).getDocumentElement();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("无法解析 Excel XML", ex);
        }
    }

    public static String text(Map<String, String> row, String... names) {
        for (String name : names) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey().startsWith(name) && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue().trim();
                }
            }
        }
        return "";
    }

}
