package com.pcelica.pdf;

import com.pcelica.model.BeeUser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PdfExporter {
    private static final DateTimeFormatter OUT_DF = DateTimeFormatter.ofPattern("dd.MM.yyyy.");

    /**
     * Backwards-compatible entry used by MainFrame: export to Desktop folder.
     * It will try to use the template (pravi_primjer.pdf) if present; otherwise falls back to generated layout.
     */
    public static File exportToDesktopFolder(BeeUser u) throws IOException {
        Path[] candidates = new Path[]{
                Paths.get("src/main/resources/templates/pravi_primjer.pdf"),
                Paths.get("templates/pravi_primjer.pdf"),
                Paths.get("pravi_primjer.pdf"),
                Paths.get("/mnt/data/pravi_primjer.pdf")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) {
                return exportToDesktopUsingTemplate(u, p);
            }
        }
        return exportToDesktopSimple(u);
    }

    private static File exportToDesktopUsingTemplate(BeeUser u, Path template) throws IOException {
        String userHome = System.getProperty("user.home");
        Path desktop = Paths.get(userHome, "Desktop");
        String folder = "Pčelica-Podsticaji-" + u.getYear();
        Path targetDir = desktop.resolve(folder);
        Files.createDirectories(targetDir);

        String fileName = String.format("%s_%s.pdf",
                u.getLastName().replaceAll("\\s+", "_"),
                u.getFirstName().replaceAll("\\s+", "_"));
        Path outPath = targetDir.resolve(fileName);

        try (PDDocument templateDoc = Loader.loadPDF(template.toFile())) {
            if (templateDoc.getNumberOfPages() == 0) {
                throw new IOException("Template PDF nema stranica.");
            }
            PDPage page = templateDoc.getPage(0);
            PDType0Font font = loadEmbeddedFont(templateDoc);
            Map<String, float[]> coords = getDefaultCoordinates();

            try (PDPageContentStream cs = new PDPageContentStream(templateDoc, page, AppendMode.APPEND, true, true)) {
                cs.setFont(font, 11);
                cs.setRenderingMode(RenderingMode.FILL);
                drawText(cs, font, 12, coords.get("org_name"), "UDRUŽENJE ZA RAZVOJ I PODRŠKU POLJOPRIVREDE \"PČELICA\"");
                drawText(cs, font, 11, coords.get("date"), "Datum: " + java.time.LocalDate.now().format(OUT_DF));
                drawText(cs, font, 11, coords.get("doc_number"), "Broj: " + safe(u.getDocNumber()));
                drawText(cs, font, 18, coords.get("title"), "POTVRDA");

                String body = String.format("Ovom potvrđujemo da se %s %s, rođen(a) %s u %s, sa prebivalištem u %s, nalazi u evidenciji aktivnih pčelara udruženja.",
                        safe(u.getFirstName()),
                        safe(u.getLastName()),
                        u.getBirthDate() == null ? "" : u.getBirthDate().format(OUT_DF),
                        safe(u.getBirthPlace()),
                        safe(u.getResidenceCity()));

                List<String> lines = wrap(body, 80);
                float[] bodyBase = coords.get("body");
                float y = bodyBase[1];
                for (String ln : lines) {
                    drawText(cs, font, 12, new float[]{bodyBase[0], y}, ln);
                    y -= 16;
                }

                drawText(cs, font, 12, coords.get("colonies"), "Broj pčelinjih zajednica: " + u.getColonies());
                drawText(cs, font, 12, coords.get("sig_title"), "Predsjednik Udruženja");
                drawText(cs, font, 12, coords.get("sig_line"), "________________________");
                drawText(cs, font, 12, coords.get("sig_name"), "Šahim Halilbašić");
            }

            templateDoc.save(outPath.toFile());
            return outPath.toFile();
        }
    }

    private static File exportToDesktopSimple(BeeUser u) throws IOException {
        Path exports = Paths.get("exports");
        if (!Files.exists(exports)) Files.createDirectories(exports);

        String fileName = String.format("Potvrda_%s_%s_%d.pdf",
                u.getLastName().replaceAll("\\s+", "_"),
                u.getFirstName().replaceAll("\\s+", "_"),
                u.getYear());
        Path outExports = exports.resolve(fileName);

        String userHome = System.getProperty("user.home");
        Path desktop = Paths.get(userHome, "Desktop");
        Path targetDir = desktop.resolve("Pčelica-Podsticaji-" + u.getYear());
        Files.createDirectories(targetDir);
        Path outDesktop = targetDir.resolve(fileName);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDType0Font font = loadEmbeddedFont(doc);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float marginLeft = 50;
                float y = 750;

                drawText(cs, font, 12, new float[]{marginLeft, y}, "UDRUŽENJE ZA RAZVOJ I PODRŠKU POLJOPRIVREDE \"PČELICA\"");
                y -= 25;
                drawText(cs, font, 11, new float[]{marginLeft, y}, "Datum: " + java.time.LocalDate.now().format(OUT_DF));
                y -= 20;
                drawText(cs, font, 11, new float[]{marginLeft, y}, "Broj: " + safe(u.getDocNumber()));
                y -= 40;
                drawText(cs, font, 18, new float[]{marginLeft + 150, y}, "POTVRDA");

                y -= 40;
                String body = String.format("Ovom potvrđujemo da se %s %s, rođen(a) %s u %s, sa prebivalištem u %s, nalazi u evidenciji aktivnih pčelara udruženja.",
                        safe(u.getFirstName()),
                        safe(u.getLastName()),
                        u.getBirthDate() == null ? "" : u.getBirthDate().format(OUT_DF),
                        safe(u.getBirthPlace()),
                        safe(u.getResidenceCity()));
                List<String> lines = wrap(body, 80);
                for (String ln : lines) {
                    drawText(cs, font, 12, new float[]{marginLeft, y}, ln);
                    y -= 16;
                }

                y -= 10;
                drawText(cs, font, 12, new float[]{marginLeft, y}, "Broj pčelinjih zajednica: " + u.getColonies());

                y -= 70;
                drawText(cs, font, 12, new float[]{marginLeft + 250, y}, "Predsjednik Udruženja");
                y -= 30;
                drawText(cs, font, 12, new float[]{marginLeft + 250, y}, "________________________");
                y -= 15;
                drawText(cs, font, 12, new float[]{marginLeft + 260, y}, "Šahim Halilbašić");
            }

            doc.save(outExports.toFile());
            doc.save(outDesktop.toFile());
        }
        return outDesktop.toFile();
    }

    private static PDType0Font loadEmbeddedFont(PDDocument doc) throws IOException {
        InputStream fontStream = PdfExporter.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
        if (fontStream == null) {
            throw new IOException("Nedostaje font DejaVuSans.ttf u resources/fonts. Dodaj 'src/main/resources/fonts/DejaVuSans.ttf' u projekt.");
        }
        try (InputStream is = fontStream) {
            return PDType0Font.load(doc, is, true);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void drawText(PDPageContentStream cs, PDType0Font font, float fontSize, float[] coords, String text) throws IOException {
        if (coords == null || text == null) return;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(coords[0], coords[1]);
        cs.showText(text);
        cs.endText();
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        text = text.trim();
        while (text.length() > maxChars) {
            int space = text.lastIndexOf(' ', maxChars);
            if (space <= 0) space = maxChars;
            out.add(text.substring(0, space));
            text = text.substring(space).trim();
        }
        if (!text.isEmpty()) out.add(text);
        return out;
    }

    private static Map<String, float[]> getDefaultCoordinates() {
        Map<String, float[]> m = new HashMap<>();
        m.put("org_name", new float[]{50f, 770f});
        m.put("date", new float[]{50f, 745f});
        m.put("doc_number", new float[]{50f, 725f});
        m.put("title", new float[]{220f, 680f});
        m.put("body", new float[]{50f, 620f});
        m.put("colonies", new float[]{50f, 520f});
        m.put("sig_title", new float[]{360f, 440f});
        m.put("sig_line", new float[]{360f, 410f});
        m.put("sig_name", new float[]{380f, 395f});
        return m;
    }
}
