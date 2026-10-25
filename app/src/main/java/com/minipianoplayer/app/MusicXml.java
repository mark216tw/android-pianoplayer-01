package com.minipianoplayer.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.json.JSONException;
import org.json.JSONObject;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Offline score-partwise reader. No network resolution of XML entities. */
public final class MusicXml {
    private static final int LIMIT = 8 * 1024 * 1024;
    private MusicXml() {}
    private static byte[] bounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
        int count; while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("樂譜檔案過大（上限 8 MB）");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    private static Document document(byte[] bytes) throws Exception {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.contains("<!ENTITY") || text.indexOf('\0') >= 0) throw new IOException("不支援 XML 實體或非 UTF-8 編碼");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false); factory.setExpandEntityReferences(false);
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
        return builder.parse(new ByteArrayInputStream(bytes));
    }
    public static Score read(InputStream stream) throws Exception {
        byte[] bytes = bounded(stream, LIMIT);
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            Map<String, byte[]> entries = new LinkedHashMap<>(); int total = 0, count = 0;
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > 128) throw new IOException("MXL 包含過多檔案");
                    if (entry.isDirectory()) continue;
                    byte[] data = bounded(zip, LIMIT - total); total += data.length;
                    entries.put(entry.getName(), data);
                }
            }
            byte[] container = entries.get("META-INF/container.xml");
            if (container == null) throw new IOException("MXL 缺少 META-INF/container.xml");
            Document manifest = document(container);
            var roots = manifest.getElementsByTagName("rootfile");
            if (roots.getLength() == 0) throw new IOException("MXL 未指定樂譜");
            String path = ((Element) roots.item(0)).getAttribute("full-path");
            bytes = entries.get(path);
            if (bytes == null) throw new IOException("MXL 找不到樂譜：" + path);
        }
        bytes = unwrapJson(bytes);
        return MusicXmlReader.read(document(bytes), new String(bytes, StandardCharsets.UTF_8));
    }
    public static String composer(String xml) {
        try { return MusicXmlReader.composer(document(xml.getBytes(StandardCharsets.UTF_8)).getDocumentElement()); }
        catch (Exception e) { return ""; }
    }
    public static String movementTitle(String xml) {
        try { return MusicXmlReader.movementTitle(document(xml.getBytes(StandardCharsets.UTF_8)).getDocumentElement()); }
        catch (Exception e) { return ""; }
    }
    private static byte[] unwrapJson(byte[] bytes) throws IOException {
        String content = new String(bytes, StandardCharsets.UTF_8).trim();
        if (content.startsWith("\uFEFF")) content = content.substring(1).trim();
        if (!content.startsWith("{")) return bytes;
        Object value;
        try { value = new JSONObject(content).opt("musicxml"); }
        catch (JSONException e) { throw new IOException("JSON 包裝格式無效：" + e.getMessage(), e); }
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IOException("JSON 檔案必須包含非空字串 musicxml 欄位，內容為 MusicXML XML");
        }
        String xml = ((String) value).trim();
        if (xml.startsWith("\uFEFF")) xml = xml.substring(1).trim();
        if (!xml.startsWith("<")) throw new IOException("musicxml 欄位的內容必須是 MusicXML XML");
        byte[] result = xml.getBytes(StandardCharsets.UTF_8);
        if (result.length > LIMIT) throw new IOException("樂譜檔案過大（上限 8 MB）");
        return result;
    }
}
