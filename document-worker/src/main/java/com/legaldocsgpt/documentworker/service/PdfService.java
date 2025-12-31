package com.legaldocsgpt.documentworker.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Service
public class PdfService {

    public String generatePdf(String jobId, String htmlContent) throws IOException {
        String filePath = "/app/storage/docs/" + jobId + ".pdf";

        String fullHtml = "<html><head><style>" +
                "body { font-family: 'Helvetica'; margin: 1in; line-height: 1.5; font-size: 12pt; }" +
                "h3 { text-align: center; text-transform: uppercase; margin-bottom: 20px; }" +
                "p { margin-bottom: 10px; text-align: justify; }" +
                "ul { margin-bottom: 10px; }" +
                "li { margin-bottom: 5px; }" +
                "</style></head><body>" + htmlContent + "</body></html>";

        try (OutputStream os = new FileOutputStream(filePath)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(fullHtml, "/");
            builder.toStream(os);
            builder.run();
        }
        return "/api/storage/docs/" + jobId + ".pdf";
    }
}