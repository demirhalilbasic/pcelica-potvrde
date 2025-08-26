package com.pcelica.pdf;

import com.pcelica.model.BeeUser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PdfExporter {
    private static final DateTimeFormatter OUT_DF = DateTimeFormatter.ofPattern("dd.MM.yyyy.");

    public static File exportToDesktopFolder(BeeUser u) throws IOException {
        String userHome = System.getProperty("user.home");
        Path desktop = Paths.get(userHome, "Desktop");
        String folder = "Pčelica-Podsticaji-" + u.getYear();
        Path targetDir = desktop.resolve(folder);
        Files.createDirectories(targetDir);

        String fileName = String.format("%s_%s.pdf",
                u.getLastName().replaceAll("\\s+", "_"),
                u.getFirstName().replaceAll("\\s+", "_"));
        Path outPath = targetDir.resolve(fileName);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType0Font regularFont = loadEmbeddedFont(document, "/fonts/DejaVuSans.ttf");
            PDType0Font boldFont = loadEmbeddedFont(document, "/fonts/DejaVuSans-Bold.ttf");

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Margine i razmaci
                float marginLeft = 50;
                float marginTop = 750;
                float lineHeight = 14;

                // Naziv udruženja
                String orgName = "UDRUŽENJE ZA RAZVOJ I PODRŠKU POLJOPRIVREDE \"PČELICA\"";
                float orgNameWidth = boldFont.getStringWidth(orgName) / 1000 * 12;
                float orgNameX = (PDRectangle.A4.getWidth() - orgNameWidth) / 2;
                drawText(contentStream, boldFont, 12, orgNameX, marginTop, orgName);

                // Adresa
                String address = "75 270 Živinice Gornje, Glavni put bb, naseljeno mjesto Kopjevići";
                float addressWidth = regularFont.getStringWidth(address) / 1000 * 10;
                float addressX = (PDRectangle.A4.getWidth() - addressWidth) / 2;
                drawText(contentStream, regularFont, 10, addressX, marginTop - lineHeight, address);

                // Web i e-mail
                String contact = "www.pcelica-gzivinice.weebly.com   e-mail: pcelicagzivinice@gmail.com";
                float contactWidth = regularFont.getStringWidth(contact) / 1000 * 10;
                float contactX = (PDRectangle.A4.getWidth() - contactWidth) / 2;
                drawText(contentStream, regularFont, 10, contactX, marginTop - 2 * lineHeight, contact);

                // Datum
                String dateStr = "Datum: " + LocalDate.now().format(OUT_DF) + " godine";
                drawText(contentStream, regularFont, 11, marginLeft, marginTop - 4 * lineHeight, dateStr);

                // Broj dokumenta
                String docNumber = "Broj: " + (u.getDocNumber() != null ? u.getDocNumber() : "");
                drawText(contentStream, regularFont, 11, marginLeft, marginTop - 5 * lineHeight, docNumber);

                // Kontakt
                String contactInfo = "Kontakt: 061 / 96 02 41";
                drawText(contentStream, regularFont, 11, marginLeft, marginTop - 6 * lineHeight, contactInfo);

                // Naslov POTVRDA
                String title = "P o t v r d u";
                float titleWidth = boldFont.getStringWidth(title) / 1000 * 18;
                float titleX = (PDRectangle.A4.getWidth() - titleWidth) / 2;
                drawText(contentStream, boldFont, 18, titleX, marginTop - 9 * lineHeight, title);

                // Tekst potvrde
                float bodyY = marginTop - 13 * lineHeight;
                String genderSuffix = "Žensko".equals(u.getGender()) ? "a" : "";

                StringBuilder bodyBuilder = new StringBuilder("Da se, ");
                bodyBuilder.append(safe(u.getLastName())).append(" ").append(safe(u.getFirstName()))
                        .append(", rođen").append(genderSuffix).append(" ");

                if (u.getBirthDate() != null) {
                    bodyBuilder.append(u.getBirthDate().format(OUT_DF)).append(" godine");
                }

                if (u.getBirthPlace() != null && !u.getBirthPlace().trim().isEmpty()) {
                    bodyBuilder.append(" u ").append(safe(u.getBirthPlace()));
                }

                if (u.getResidenceCity() != null && !u.getResidenceCity().trim().isEmpty()) {
                    bodyBuilder.append(", sa prebivalištem u ").append(safe(u.getResidenceCity()));
                }

                bodyBuilder.append(", nalazi u evidenciji aktivnog članstva");

                String line1 = bodyBuilder.toString();
                String line2 = "Udruženja za razvoj i podršku poljoprivrede \"PČELICA\" i broji (" +
                        u.getColonies() + ") pčelinjih zajednica / kolonija.";

                List<String> wrappedLine1 = wrapText(line1, regularFont, 11,
                        PDRectangle.A4.getWidth() - 2 * marginLeft);
                for (int i = 0; i < wrappedLine1.size(); i++) {
                    drawText(contentStream, regularFont, 11, marginLeft,
                            bodyY - i * lineHeight, wrappedLine1.get(i));
                }

                int line1Height = wrappedLine1.size();
                drawText(contentStream, regularFont, 11, marginLeft,
                        bodyY - line1Height * lineHeight, line2);

                // Redni brojevi
                String coloniesLine = "Redni broj od 1 do " + u.getColonies() + " pčelinjih zajednica / kolonija.";
                drawText(contentStream, regularFont, 11, marginLeft,
                        bodyY - (line1Height + 2) * lineHeight, coloniesLine);

                // Svrha
                float purposeY = bodyY - (line1Height + 4) * lineHeight;
                String purpose1 = "Potvrda se izdaje podnosiocu zahtjeva u svrhu ostvarivanja novčane podrške u";
                String purpose2 = "primarnoj poljoprivrednoj proizvodnji, te zdravstvenoj zaštiti pčela i unapređenje pčelinjeg";
                String purpose3 = "fonda, za " + u.getYear() + ". godinu.";

                drawText(contentStream, regularFont, 11, marginLeft, purposeY, purpose1);
                drawText(contentStream, regularFont, 11, marginLeft, purposeY - lineHeight, purpose2);
                drawText(contentStream, regularFont, 11, marginLeft, purposeY - 2 * lineHeight, purpose3);

                // Potpis
                float signatureY = purposeY - 6 * lineHeight;
                float signatureX = PDRectangle.A4.getWidth() - marginLeft - 150;

                String signatureTitle = "Predsjednik Udruženja";
                drawText(contentStream, regularFont, 11, signatureX, signatureY, signatureTitle);

                String signatureLine = "________________________";
                drawText(contentStream, regularFont, 11, signatureX, signatureY - lineHeight, signatureLine);

                // Pomaknuto malo ulijevo (-5 px)
                String signatureName = "Šahim Halilbašić";
                float nameWidth = regularFont.getStringWidth(signatureName) / 1000 * 11;
                float nameX = signatureX + (150 - nameWidth) / 2 - 8;
                drawText(contentStream, regularFont, 11, nameX, signatureY - 2 * lineHeight, signatureName);
            }

            document.save(outPath.toFile());
            return outPath.toFile();
        }
    }

    private static PDType0Font loadEmbeddedFont(PDDocument doc, String fontPath) throws IOException {
        InputStream fontStream = PdfExporter.class.getResourceAsStream(fontPath);
        if (fontStream == null) {
            throw new IOException("Nedostaje font: " + fontPath);
        }
        try (InputStream is = fontStream) {
            return PDType0Font.load(doc, is, true);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void drawText(PDPageContentStream contentStream, PDType0Font font,
                                 float fontSize, float x, float y, String text) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }

    private static List<String> wrapText(String text, PDType0Font font,
                                         float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            float testWidth = font.getStringWidth(testLine) / 1000 * fontSize;

            if (testWidth <= maxWidth) {
                currentLine.append(currentLine.length() > 0 ? " " + word : word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    int splitIndex = (int) (maxWidth / (font.getStringWidth("a") / 1000 * fontSize));
                    if (splitIndex == 0) splitIndex = 1;

                    while (word.length() > 0) {
                        if (word.length() <= splitIndex) {
                            currentLine.append(word);
                            word = "";
                        } else {
                            String part = word.substring(0, splitIndex);
                            float partWidth = font.getStringWidth(part) / 1000 * fontSize;

                            if (partWidth <= maxWidth) {
                                lines.add(part);
                                word = word.substring(splitIndex);
                            } else {
                                splitIndex--;
                            }
                        }
                    }
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
