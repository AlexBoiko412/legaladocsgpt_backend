package com.legaldocsgpt.documentworker.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfService {

    public String generatePdf(String jobId, String content) throws IOException {
        String outputDir = "/app/storage/docs/";
        new File(outputDir).mkdirs();
        String filePath = outputDir + jobId + ".pdf";

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 11;
            float leading = 1.5f * fontSize;
            float margin = 50;
            float width = page.getMediaBox().getWidth() - 2 * margin;
            float yPosition = page.getMediaBox().getHeight() - margin;

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(margin, yPosition);

            // Split AI text by paragraphs
            String[] paragraphs = content.split("\\R");
            for (String paragraph : paragraphs) {
                List<String> wrappedLines = wrapText(paragraph, font, fontSize, width);
                for (String line : wrappedLines) {
                    // Check for Page Overflow
                    if (yPosition < margin + leading) {
                        contentStream.endText();
                        contentStream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        contentStream.beginText();
                        contentStream.setFont(font, fontSize);
                        yPosition = page.getMediaBox().getHeight() - margin;
                        contentStream.newLineAtOffset(margin, yPosition);
                    }
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -leading);
                    yPosition -= leading;
                }
                // Paragraph break
                contentStream.newLineAtOffset(0, -leading / 2);
                yPosition -= leading / 2;
            }
            contentStream.endText();
            contentStream.close();
            document.save(filePath);
        }
        return "/api/storage/docs/" + jobId + ".pdf";
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String test = currentLine + (!currentLine.isEmpty() ? " " : "") + word;
            float textWidth = font.getStringWidth(test) / 1000 * fontSize;
            if (textWidth > width) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine.append(!currentLine.isEmpty() ? " " : "").append(word);
            }
        }
        lines.add(currentLine.toString());
        return lines;
    }
}