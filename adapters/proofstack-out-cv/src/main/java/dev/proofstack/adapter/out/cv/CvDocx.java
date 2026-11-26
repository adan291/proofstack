package dev.proofstack.adapter.out.cv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.proofstack.adapter.out.cv.Cv.Contact;

/**
 * The CV as a Word document, written as Office Open XML without any library. Same model and order as
 * {@link CvMarkdown}. ATS-friendly on purpose: one column, native heading styles, real bullet lists, no tables,
 * images or text boxes. Links are written out as text, which every parser reads.
 */
final class CvDocx {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final int BULLETS = 1;

    private CvDocx() {
    }

    static byte[] render(Cv cv) {
        StringBuilder body = new StringBuilder();
        CvText t = cv.t();
        paragraph(body, "Title", cv.name());
        paragraph(body, "Subtitle", cv.headline());
        if (!cv.contact().isEmpty()) {
            paragraph(body, null, cv.contact().stream().map(CvDocx::contact).collect(Collectors.joining(" · ")));
        }
        if (!cv.summary().isEmpty()) {
            paragraph(body, "Heading1", t.profile());
            paragraph(body, null, cv.summary());
        }
        if (!cv.skills().isEmpty()) {
            paragraph(body, "Heading1", t.skills());
            cv.skills().forEach(g -> labelled(body, g.area(), g.names()));
        }
        if (!cv.jobs().isEmpty()) {
            paragraph(body, "Heading1", t.experience());
            cv.jobs().forEach(job -> {
                paragraph(body, "Heading2", job.role() + " · " + job.company());
                meta(body, job.meta());
                bullets(body, job.highlights());
                if (!job.technologies().isEmpty()) {
                    labelled(body, t.technologies(), job.technologies());
                }
            });
        }
        if (!cv.projects().isEmpty()) {
            paragraph(body, "Heading1", t.projects());
            cv.projects().forEach(p -> {
                paragraph(body, "Heading2", p.name());
                paragraph(body, null, p.summary());
                if (!p.stack().isEmpty()) {
                    bullet(body, t.stack() + ": " + p.stack());
                }
                if (!p.result().isEmpty()) {
                    bullet(body, t.result() + ": " + p.result());
                }
                if (p.hasLink()) {
                    bullet(body, (p.linkLabel().isEmpty() ? "" : p.linkLabel() + ": ") + p.linkUrl());
                }
            });
        }
        if (!cv.studies().isEmpty()) {
            paragraph(body, "Heading1", t.education());
            cv.studies().forEach(s -> {
                paragraph(body, "Heading2", s.title() + " · " + s.institution());
                meta(body, s.meta());
                bullets(body, s.highlights());
            });
        }
        if (!cv.certifications().isEmpty()) {
            paragraph(body, "Heading1", t.certifications());
            cv.certifications().forEach(c -> bullet(body, c.title()
                    + (c.detail().isEmpty() ? "" : " — " + c.detail())
                    + (c.linked() ? " — " + c.url() : "")));
        }
        if (!cv.languages().isEmpty()) {
            paragraph(body, "Heading1", t.languages());
            cv.languages().forEach(l -> bullet(body, l.name() + (l.level().isEmpty() ? "" : " (" + l.level() + ")")));
        }
        return zip(document(body.toString()), styles(cv.t().lang()));
    }

    private static String contact(Contact c) {
        return c.linked() && !c.url().startsWith("mailto:") ? c.text() + ": " + c.url() : c.text();
    }

    // ------------------------------------------------------------------ paragraphs

    private static void paragraph(StringBuilder body, String style, String text) {
        body.append("<w:p>");
        if (style != null) {
            body.append("<w:pPr><w:pStyle w:val=\"").append(style).append("\"/></w:pPr>");
        }
        run(body, text, false);
        body.append("</w:p>");
    }

    private static void meta(StringBuilder body, String meta) {
        if (!meta.isEmpty()) {
            paragraph(body, "Meta", meta);
        }
    }

    /** "Label: text" with the label in bold, e.g. a skill group. */
    private static void labelled(StringBuilder body, String label, String text) {
        body.append("<w:p>");
        run(body, label + ": ", true);
        run(body, text, false);
        body.append("</w:p>");
    }

    private static void bullets(StringBuilder body, List<String> items) {
        items.forEach(item -> bullet(body, item));
    }

    private static void bullet(StringBuilder body, String text) {
        body.append("<w:p><w:pPr><w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"")
                .append(BULLETS).append("\"/></w:numPr></w:pPr>");
        run(body, text, false);
        body.append("</w:p>");
    }

    private static void run(StringBuilder body, String text, boolean bold) {
        body.append("<w:r>");
        if (bold) {
            body.append("<w:rPr><w:b/></w:rPr>");
        }
        body.append("<w:t xml:space=\"preserve\">").append(xml(text)).append("</w:t></w:r>");
    }

    /** XML escape, and drops the control characters XML 1.0 does not allow. */
    static String xml(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            switch (cp) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (cp == 0x9 || cp >= 0x20 && cp != 0xFFFE && cp != 0xFFFF) {
                        out.appendCodePoint(cp);
                    }
                }
            }
        });
        return out.toString();
    }

    // ------------------------------------------------------------------ package parts

    private static String document(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"" + W + "\"><w:body>" + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"850\" w:right=\"964\" w:bottom=\"850\" w:left=\"964\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/>"
                + "</w:sectPr></w:body></w:document>";
    }

    private static String styles(String lang) {
        String locale = "es".equals(lang) ? "es-ES" : "en-GB";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"" + W + "\">"
                + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>"
                + "<w:sz w:val=\"20\"/><w:lang w:val=\"" + locale + "\"/></w:rPr></w:rPrDefault>"
                + "<w:pPrDefault><w:pPr><w:spacing w:after=\"60\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault>"
                + "</w:docDefaults>"
                + style("Normal", "Normal", null, "", "")
                + style("Title", "Title", "Normal", "<w:spacing w:after=\"0\"/>", "<w:b/><w:color w:val=\"1F4E79\"/><w:sz w:val=\"40\"/>")
                + style("Subtitle", "Subtitle", "Normal", "<w:spacing w:after=\"80\"/>", "<w:color w:val=\"3B434B\"/><w:sz w:val=\"24\"/>")
                + style("Heading1", "heading 1", "Normal",
                "<w:keepNext/><w:spacing w:before=\"200\" w:after=\"80\"/><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"1F4E79\"/></w:pBdr><w:outlineLvl w:val=\"0\"/>",
                "<w:b/><w:caps/><w:color w:val=\"1F4E79\"/><w:sz w:val=\"22\"/>")
                + style("Heading2", "heading 2", "Normal",
                "<w:keepNext/><w:spacing w:before=\"120\" w:after=\"0\"/><w:outlineLvl w:val=\"1\"/>", "<w:b/><w:sz w:val=\"21\"/>")
                + style("Meta", "CV meta", "Normal", "<w:spacing w:after=\"40\"/>", "<w:color w:val=\"5A636B\"/><w:sz w:val=\"19\"/>")
                + style("ListParagraph", "List Paragraph", "Normal", "<w:spacing w:after=\"20\"/><w:ind w:left=\"360\"/>", "")
                + "</w:styles>";
    }

    private static String style(String id, String name, String basedOn, String pPr, String rPr) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"" + id + "\"><w:name w:val=\"" + name + "\"/>"
                + (basedOn == null ? "" : "<w:basedOn w:val=\"" + basedOn + "\"/>") + "<w:qFormat/>"
                + (pPr.isEmpty() ? "" : "<w:pPr>" + pPr + "</w:pPr>")
                + (rPr.isEmpty() ? "" : "<w:rPr>" + rPr + "</w:rPr>") + "</w:style>";
    }

    private static final String NUMBERING = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:numbering xmlns:w=\"" + W + "\">"
            + "<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"singleLevel\"/>"
            + "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/>"
            + "<w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"360\" w:hanging=\"240\"/></w:pPr></w:lvl></w:abstractNum>"
            + "<w:num w:numId=\"" + BULLETS + "\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>";

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
            + "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";

    private static final String DOCUMENT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>"
            + "</Relationships>";

    private static byte[] zip(String document, String styles) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "[Content_Types].xml", CONTENT_TYPES);
            entry(zip, "_rels/.rels", ROOT_RELS);
            entry(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS);
            entry(zip, "word/document.xml", document);
            entry(zip, "word/styles.xml", styles);
            entry(zip, "word/numbering.xml", NUMBERING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0); // reproducible output: the same profile always gives the same bytes
        zip.putNextEntry(entry);
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
