package com.legaldocsgpt.documentworker.service;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for WordProcessingService.
 *
 * No Spring context - just pure Java.
 * DOCX fixtures are built programmatically using docx4j so there are
 * no binary files to maintain in src/test/resources.
 *
 * Test structure:
 *  - DocxFixtures (inner helper)  - builds reusable DOCX byte arrays
 *  - ExtractContentTests          - tests for extractContent()
 *  - AssembleDocumentTests        - tests for assembleDocument()
 *  - ParseMarkdownTests           - tests for the markdown - paragraphs logic
 *                                   (tested indirectly through assembleDocument)
 */
class WordProcessingServiceTest {

    private WordProcessingService service;

    @BeforeEach
    void setUp() {
        service = new WordProcessingService();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOCX FIXTURE BUILDER
    // Builds minimal valid DOCX files programmatically - no binary files needed.
    // ─────────────────────────────────────────────────────────────────────────

    static class DocxFixtures {

        private static final ObjectFactory FACTORY = new ObjectFactory();

        /**
         * Creates a DOCX that mimics a real shell template:
         *   - Header paragraph
         *   - Variable: ${employeeName}
         *   - ${CONTENT} placeholder
         *   - SIGNATURES heading (content extraction stops here)
         *   - Signature lines
         */
        static byte[] shellTemplate(String... extraParagraphsBeforeSignatures) throws Exception {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            MainDocumentPart mdp = pkg.getMainDocumentPart();
            Body body = mdp.getContents().getBody();

            // Header
            body.getContent().add(makeParagraph("EMPLOYMENT AGREEMENT"));

            // Variable that should be replaced by userData
            body.getContent().add(makeParagraph("Employee: ${employeeName}"));
            body.getContent().add(makeParagraph("Start Date: ${startDate}"));

            // Extra paragraphs if provided
            for (String text : extraParagraphsBeforeSignatures) {
                body.getContent().add(makeParagraph(text));
            }

            // The content placeholder - assembleDocument finds and replaces this
            body.getContent().add(makeParagraph("${CONTENT}"));

            // Signature block - extractContent must stop before this
            body.getContent().add(makeParagraph("SIGNATURES"));
            body.getContent().add(makeParagraph("Employer Signature: ___________"));
            body.getContent().add(makeParagraph("Employee Signature: ___________"));

            return toBytes(pkg);
        }

        /**
         * Creates a DOCX that has NO ${CONTENT} placeholder.
         * assembleDocument should fall back gracefully.
         */
        static byte[] shellTemplateWithoutContentPlaceholder() throws Exception {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            MainDocumentPart mdp = pkg.getMainDocumentPart();
            Body body = mdp.getContents().getBody();

            body.getContent().add(makeParagraph("NDA AGREEMENT"));
            body.getContent().add(makeParagraph("Party: ${partyName}"));
            body.getContent().add(makeParagraph("SIGNATURES"));
            body.getContent().add(makeParagraph("Signature: ___________"));

            return toBytes(pkg);
        }

        /**
         * Creates a DOCX that simulates an already-assembled document
         * (what extractContent reads from MinIO to use as AI context).
         */
        static byte[] assembledDocument(String... bodyParagraphs) throws Exception {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            MainDocumentPart mdp = pkg.getMainDocumentPart();
            Body body = mdp.getContents().getBody();

            body.getContent().add(makeParagraph("EMPLOYMENT AGREEMENT"));
            body.getContent().add(makeParagraph("Employee: John Smith"));

            for (String text : bodyParagraphs) {
                body.getContent().add(makeParagraph(text));
            }

            body.getContent().add(makeParagraph("SIGNATURES"));
            body.getContent().add(makeParagraph("Employer Signature: ___________"));

            return toBytes(pkg);
        }

        /**
         * Creates a DOCX with no SIGNATURES heading at all.
         * extractContent should return everything.
         */
        static byte[] documentWithoutSignatureBlock(String... paragraphs) throws Exception {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            Body body = pkg.getMainDocumentPart().getContents().getBody();

            for (String text : paragraphs) {
                body.getContent().add(makeParagraph(text));
            }

            return toBytes(pkg);
        }

        private static P makeParagraph(String text) {
            P p = FACTORY.createP();
            R r = FACTORY.createR();
            Text t = FACTORY.createText();
            t.setValue(text);
            t.setSpace("preserve");
            r.getContent().add(t);
            p.getContent().add(r);
            return p;
        }

        private static byte[] toBytes(WordprocessingMLPackage pkg) throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pkg.save(baos);
            return baos.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // extractContent() tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractContent()")
    class ExtractContentTests {

        @Test
        @DisplayName("returns body paragraphs and stops before SIGNATURES heading")
        void shouldExtractBodyAndStopAtSignatures() throws Exception {
            byte[] docx = DocxFixtures.assembledDocument(
                    "Article 1 - Employment",
                    "The employee shall commence work on the agreed date.",
                    "Article 2 - Compensation",
                    "The employee shall receive a monthly salary."
            );

            String result = service.extractContent(docx);

            assertThat(result).contains("Article 1 - Employment");
            assertThat(result).contains("Article 2 - Compensation");
            assertThat(result).contains("The employee shall commence work");
            assertThat(result).doesNotContain("SIGNATURES");
            assertThat(result).doesNotContain("Employer Signature");
        }

        @Test
        @DisplayName("stops at EMPLOYER SIGNATURE variant")
        void shouldStopAtEmployerSignatureVariant() throws Exception {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            Body body = pkg.getMainDocumentPart().getContents().getBody();

            body.getContent().add(DocxFixtures.makeParagraph("Article 1 - Terms"));
            body.getContent().add(DocxFixtures.makeParagraph("EMPLOYER SIGNATURE"));
            body.getContent().add(DocxFixtures.makeParagraph("Should not appear"));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pkg.save(baos);

            String result = service.extractContent(baos.toByteArray());

            assertThat(result).contains("Article 1 - Terms");
            assertThat(result).doesNotContain("EMPLOYER SIGNATURE");
            assertThat(result).doesNotContain("Should not appear");
        }

        @Test
        @DisplayName("returns all content when no signature heading exists")
        void shouldReturnAllContentWhenNoSignatureBlock() throws Exception {
            byte[] docx = DocxFixtures.documentWithoutSignatureBlock(
                    "Paragraph One",
                    "Paragraph Two",
                    "Paragraph Three"
            );

            String result = service.extractContent(docx);

            assertThat(result).contains("Paragraph One");
            assertThat(result).contains("Paragraph Two");
            assertThat(result).contains("Paragraph Three");
        }

        @Test
        @DisplayName("skips blank paragraphs")
        void shouldSkipBlankParagraphs() throws Exception {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
            Body body = pkg.getMainDocumentPart().getContents().getBody();

            body.getContent().add(DocxFixtures.makeParagraph(""));
            body.getContent().add(DocxFixtures.makeParagraph("   "));
            body.getContent().add(DocxFixtures.makeParagraph("Real content"));
            body.getContent().add(DocxFixtures.makeParagraph(""));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pkg.save(baos);

            String result = service.extractContent(baos.toByteArray());

            assertThat(result).isEqualTo("Real content");
        }

        @Test
        @DisplayName("returns empty string for document with only blank paragraphs")
        void shouldReturnEmptyStringForBlankDocument() throws Exception {
            byte[] docx = DocxFixtures.documentWithoutSignatureBlock("", "   ", "");

            String result = service.extractContent(docx);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("trims leading and trailing whitespace from result")
        void shouldTrimResult() throws Exception {
            byte[] docx = DocxFixtures.assembledDocument("Some content here");

            String result = service.extractContent(docx);

            assertThat(result).doesNotStartWith("\n");
            assertThat(result).doesNotEndWith("\n");
            assertThat(result).isEqualTo(result.trim());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // assembleDocument() tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assembleDocument()")
    class AssembleDocumentTests {

        @Test
        @DisplayName("replaces ${CONTENT} placeholder with AI-generated content")
        void shouldReplaceContentPlaceholder() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            String aiContent = "Article 1 - Terms\nThe parties agree to the following terms.";
            Map<String, String> userData = Map.of(
                    "employeeName", "John Smith",
                    "startDate", "2024-01-15"
            );

            byte[] result = service.assembleDocument(shell, aiContent, userData);

            String extracted = service.extractContent(result);
            assertThat(extracted).contains("Article 1 - Terms");
            assertThat(extracted).contains("The parties agree to the following terms");
        }

        @Test
        @DisplayName("replaces user data variables in the document")
        void shouldReplaceUserDataVariables() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of(
                    "employeeName", "Jane Doe",
                    "startDate", "2024-03-01"
            );

            byte[] result = service.assembleDocument(shell, "Some content", userData);

            // Read the full document text to verify variable replacement
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));
            String fullText = extractAllText(pkg);

            assertThat(fullText).contains("Jane Doe");
            assertThat(fullText).contains("2024-03-01");
            assertThat(fullText).doesNotContain("${employeeName}");
            assertThat(fullText).doesNotContain("${startDate}");
        }

        @Test
        @DisplayName("does not include ${CONTENT} placeholder in output")
        void shouldNotIncludeContentPlaceholderInOutput() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");

            byte[] result = service.assembleDocument(shell, "Actual content", userData);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));
            String fullText = extractAllText(pkg);

            assertThat(fullText).doesNotContain("${CONTENT}");
            assertThat(fullText).doesNotContain("CONTENT");
        }

        @Test
        @DisplayName("preserves signature block after content injection")
        void shouldPreserveSignatureBlock() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");

            byte[] result = service.assembleDocument(shell, "Some AI content", userData);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));
            String fullText = extractAllText(pkg);

            assertThat(fullText).contains("SIGNATURES");
            assertThat(fullText).contains("Employer Signature");
        }

        @Test
        @DisplayName("falls back gracefully when ${CONTENT} placeholder is missing")
        void shouldFallBackWhenNoContentPlaceholder() throws Exception {
            byte[] shell = DocxFixtures.shellTemplateWithoutContentPlaceholder();
            Map<String, String> userData = Map.of("partyName", "Acme Corp");

            // Should not throw - just returns the document with variables replaced
            byte[] result = service.assembleDocument(shell, "AI content that won't be injected", userData);

            assertThat(result).isNotEmpty();
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));
            String fullText = extractAllText(pkg);

            assertThat(fullText).contains("Acme Corp");
            assertThat(fullText).doesNotContain("${partyName}");
        }

        @Test
        @DisplayName("handles empty content string without throwing")
        void shouldHandleEmptyContent() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");

            // Empty content - parseMarkdownToParagraphs returns empty list, no paragraphs inserted
            assertThatCode(() -> service.assembleDocument(shell, "", userData))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handles null content without throwing")
        void shouldHandleNullContent() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");

            assertThatCode(() -> service.assembleDocument(shell, null, userData))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("produces valid DOCX bytes that can be loaded by docx4j")
        void shouldProduceValidDocxBytes() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "Test", "startDate", "2024-01-01");

            byte[] result = service.assembleDocument(shell, "Test content", userData);

            // If this doesn't throw, the bytes are a valid DOCX file
            assertThatCode(() -> WordprocessingMLPackage.load(new ByteArrayInputStream(result)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("content is inserted at the position of the ${CONTENT} placeholder")
        void shouldInsertContentAtCorrectPosition() throws Exception {
            // Shell has: Header - ${employeeName} - ${CONTENT} - SIGNATURES
            // After assembly: Header - John Smith - [AI content] - SIGNATURES
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John Smith", "startDate", "2024-01-01");
            String aiContent = "INJECTED CLAUSE - This is the AI content";

            byte[] result = service.assembleDocument(shell, aiContent, userData);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));
            String fullText = extractAllText(pkg);

            // Verify order: header before content, content before signatures
            int headerPos = fullText.indexOf("EMPLOYMENT AGREEMENT");
            int contentPos = fullText.indexOf("INJECTED CLAUSE");
            int signaturesPos = fullText.indexOf("SIGNATURES");

            assertThat(headerPos).isLessThan(contentPos);
            assertThat(contentPos).isLessThan(signaturesPos);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Markdown parsing tests (via assembleDocument)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Markdown parsing (via assembleDocument)")
    class MarkdownParsingTests {

        @Test
        @DisplayName("converts ## heading to Heading2 style paragraph")
        void shouldConvertH2Heading() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");
            String markdown = "## Article 1 - Employment Terms";

            byte[] result = service.assembleDocument(shell, markdown, userData);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));

            // Find the paragraph with our heading text and verify it has Heading2 style
            boolean foundHeading = pkg.getMainDocumentPart().getContents().getBody()
                    .getContent().stream()
                    .filter(obj -> obj instanceof P)
                    .map(obj -> (P) obj)
                    .filter(p -> p.getPPr() != null
                            && p.getPPr().getPStyle() != null
                            && "Heading2".equals(p.getPPr().getPStyle().getVal()))
                    .anyMatch(p -> extractTextFromP(p).contains("Article 1 - Employment Terms"));

            assertThat(foundHeading)
                    .as("Expected a Heading2 paragraph containing 'Article 1 - Employment Terms'")
                    .isTrue();
        }

        @Test
        @DisplayName("converts * bullet to ListParagraph style")
        void shouldConvertBulletPoint() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");
            String markdown = "* First bullet point";

            byte[] result = service.assembleDocument(shell, markdown, userData);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));

            boolean foundBullet = pkg.getMainDocumentPart().getContents().getBody()
                    .getContent().stream()
                    .filter(obj -> obj instanceof P)
                    .map(obj -> (P) obj)
                    .filter(p -> p.getPPr() != null
                            && p.getPPr().getPStyle() != null
                            && "ListParagraph".equals(p.getPPr().getPStyle().getVal()))
                    .anyMatch(p -> extractTextFromP(p).contains("First bullet point"));

            assertThat(foundBullet)
                    .as("Expected a ListParagraph containing 'First bullet point'")
                    .isTrue();
        }

        @Test
        @DisplayName("converts - bullet (dash variant) to ListParagraph style")
        void shouldConvertDashBullet() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");

            byte[] result = service.assembleDocument(shell, "- Dash bullet", userData);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));

            boolean foundBullet = pkg.getMainDocumentPart().getContents().getBody()
                    .getContent().stream()
                    .filter(obj -> obj instanceof P)
                    .map(obj -> (P) obj)
                    .filter(p -> p.getPPr() != null
                            && p.getPPr().getPStyle() != null
                            && "ListParagraph".equals(p.getPPr().getPStyle().getVal()))
                    .anyMatch(p -> extractTextFromP(p).contains("Dash bullet"));

            assertThat(foundBullet).isTrue();
        }

        @Test
        @DisplayName("creates correct number of paragraphs for multi-line markdown")
        void shouldCreateCorrectNumberOfParagraphs() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");
            String markdown = """
                    ## Article 1
                    First clause content.
                    * Bullet one
                    * Bullet two
                    """;

            byte[] result = service.assembleDocument(shell, markdown, userData);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result));

            // Count non-empty paragraphs that came from markdown
            // (heading + 1 normal + 2 bullets = 4 minimum)
            long markdownParagraphs = pkg.getMainDocumentPart().getContents().getBody()
                    .getContent().stream()
                    .filter(obj -> obj instanceof P)
                    .map(obj -> (P) obj)
                    .filter(p -> {
                        String text = extractTextFromP(p);
                        return text.contains("Article 1")
                                || text.contains("First clause")
                                || text.contains("Bullet one")
                                || text.contains("Bullet two");
                    })
                    .count();

            assertThat(markdownParagraphs).isGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("handles Windows-style CRLF line endings in markdown")
        void shouldHandleCrlfLineEndings() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "John", "startDate", "2024-01-01");
            String markdownWithCrlf = "## Heading\r\nParagraph content\r\n* Bullet";

            assertThatCode(() -> service.assembleDocument(shell, markdownWithCrlf, userData))
                    .doesNotThrowAnyException();

            byte[] result = service.assembleDocument(shell, markdownWithCrlf, userData);
            String fullText = extractAllText(WordprocessingMLPackage.load(
                    new ByteArrayInputStream(result)));

            assertThat(fullText).contains("Heading");
            assertThat(fullText).contains("Paragraph content");
            assertThat(fullText).contains("Bullet");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Round-trip tests
    // assembleDocument - extractContent should return the injected AI content
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Round-trip: assembleDocument - extractContent")
    class RoundTripTests {

        @Test
        @DisplayName("extracted content matches injected AI content")
        void shouldRoundTripContent() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "Alice", "startDate", "2024-06-01");
            String aiContent = """
                    ## Article 1 - Duties
                    The employee shall perform all duties as assigned.
                    
                    ## Article 2 - Compensation
                    The base salary shall be agreed upon separately.
                    """.trim();

            byte[] assembled = service.assembleDocument(shell, aiContent, userData);
            String extracted = service.extractContent(assembled);

            assertThat(extracted).contains("Article 1 - Duties");
            assertThat(extracted).contains("The employee shall perform all duties");
            assertThat(extracted).contains("Article 2 - Compensation");
            assertThat(extracted).contains("The base salary shall be agreed upon separately");
        }

        @Test
        @DisplayName("extracted content does not include variables or signature block")
        void shouldNotIncludeVariablesOrSignaturesInExtractedContent() throws Exception {
            byte[] shell = DocxFixtures.shellTemplate();
            Map<String, String> userData = Map.of("employeeName", "Bob", "startDate", "2024-07-01");

            byte[] assembled = service.assembleDocument(shell, "Some clause content", userData);
            String extracted = service.extractContent(assembled);

            assertThat(extracted).doesNotContain("${employeeName}");
            assertThat(extracted).doesNotContain("SIGNATURES");
            assertThat(extracted).doesNotContain("Employer Signature");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String extractAllText(WordprocessingMLPackage pkg) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Object obj : pkg.getMainDocumentPart().getContents().getBody().getContent()) {
                if (obj instanceof P p) {
                    sb.append(extractTextFromP(p)).append("\n");
                }
            }
        } catch (Docx4JException e) {
            throw new RuntimeException("Failed to extract text from document", e);
        }
        return sb.toString();
    }

    private String extractTextFromP(P p) {
        StringBuilder sb = new StringBuilder();
        for (Object child : p.getContent()) {
            Object unwrapped = child instanceof jakarta.xml.bind.JAXBElement
                    ? ((jakarta.xml.bind.JAXBElement<?>) child).getValue()
                    : child;
            if (unwrapped instanceof R r) {
                for (Object rc : r.getContent()) {
                    Object ru = rc instanceof jakarta.xml.bind.JAXBElement
                            ? ((jakarta.xml.bind.JAXBElement<?>) rc).getValue()
                            : rc;
                    if (ru instanceof Text t) sb.append(t.getValue());
                }
            }
        }
        return sb.toString();
    }

    private static P makeParagraph(String text) {
        return DocxFixtures.makeParagraph(text);
    }
}