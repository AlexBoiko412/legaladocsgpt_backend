package com.legaldocsgpt.documentworker.service;

import lombok.extern.slf4j.Slf4j;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;

import jakarta.xml.bind.JAXBElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class WordProcessingService {

    private static final org.docx4j.wml.ObjectFactory WML_FACTORY = new org.docx4j.wml.ObjectFactory();

    public byte[] assembleDocument(byte[] shellBytes, String content, Map<String, String> userData) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(shellBytes));
        MainDocumentPart mdp = pkg.getMainDocumentPart();

        VariablePrepare.prepare(pkg);
        Map<String, String> mappings = new HashMap<>(userData);
        mappings.put("CONTENT", "CONTENT_PLACEHOLDER_TEMP");
        mdp.variableReplace(mappings);

        Body body = mdp.getContents().getBody();

        List<Object> bodyChildren = body.getContent();

        int contentIndex = -1;
        for (int i = 0; i < bodyChildren.size(); i++) {
            Object obj = bodyChildren.get(i);
            if (obj instanceof P p) {
                String text = extractText(p);
                if (text.contains("CONTENT_PLACEHOLDER_TEMP")) {
                    contentIndex = i;
                    break;
                }
            }
        }

        if (contentIndex == -1) {
            log.warn("${CONTENT} placeholder not found in shell document");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pkg.save(baos);
            return baos.toByteArray();
        }

        bodyChildren.remove(contentIndex);

        List<P> replacements = parseMarkdownToParagraphs(content);

        for (int i = replacements.size() - 1; i >= 0; i--) {
            bodyChildren.add(contentIndex, replacements.get(i));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        pkg.save(baos);
        return baos.toByteArray();
    }

    private String extractText(P p) {
        StringBuilder sb = new StringBuilder();
        for (Object child : p.getContent()) {
            Object unwrapped = child instanceof JAXBElement ? ((JAXBElement<?>) child).getValue() : child;
            if (unwrapped instanceof R r) {
                for (Object rc : r.getContent()) {
                    Object ru = rc instanceof JAXBElement ? ((JAXBElement<?>) rc).getValue() : rc;
                    if (ru instanceof Text t) sb.append(t.getValue());
                }
            }
        }
        return sb.toString();
    }

    private List<P> parseMarkdownToParagraphs(String markdown) {
        List<P> result = new ArrayList<>();
        String[] lines = markdown.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                result.add(makeEmptyParagraph());
                continue;
            }

            if (trimmed.startsWith("### ")) {
                result.add(makeHeading(trimmed.substring(4), 3));
            } else if (trimmed.startsWith("## ")) {
                result.add(makeHeading(trimmed.substring(3), 2));
            } else if (trimmed.startsWith("# ")) {
                result.add(makeHeading(trimmed.substring(2), 1));
            }
            else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                result.add(makeBullet(trimmed.substring(2)));
            }
            else if (trimmed.isEmpty()) {
                result.add(makeEmptyParagraph());
            }
            else {
                result.add(makeNormalParagraph(trimmed));
            }
        }

        return result;
    }

    private P makeEmptyParagraph() {
        return WML_FACTORY.createP();
    }

    private P makeHeading(String text, int level) {
        P p = WML_FACTORY.createP();
        PPr pPr = WML_FACTORY.createPPr();
        Jc jc = WML_FACTORY.createJc();
        jc.setVal(JcEnumeration.LEFT);
        pPr.setJc(jc);

        String styleId = switch (level) {
            case 1 -> "Heading1";
            case 2 -> "Heading2";
            default -> "Heading3";
        };
        PPrBase.PStyle pStyle = WML_FACTORY.createPPrBasePStyle();
        pStyle.setVal(styleId);
        pPr.setPStyle(pStyle);
        p.setPPr(pPr);

        String cleaned = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        R r = makeRun(cleaned, true, false);
        p.getContent().add(r);
        return p;
    }

    private P makeBullet(String text) {
        P p = WML_FACTORY.createP();
        PPr pPr = WML_FACTORY.createPPr();

        PPrBase.PStyle pStyle = WML_FACTORY.createPPrBasePStyle();
        pStyle.setVal("ListParagraph");
        pPr.setPStyle(pStyle);

        PPrBase.NumPr numPr = WML_FACTORY.createPPrBaseNumPr();
        PPrBase.NumPr.Ilvl ilvl = WML_FACTORY.createPPrBaseNumPrIlvl();
        ilvl.setVal(BigInteger.ZERO);
        numPr.setIlvl(ilvl);
        pPr.setNumPr(numPr);

        PPrBase.Ind ind = WML_FACTORY.createPPrBaseInd();
        ind.setLeft(BigInteger.valueOf(720));
        pPr.setInd(ind);

        p.setPPr(pPr);
        addInlineRuns(p, text);
        return p;
    }

    private P makeNormalParagraph(String text) {
        P p = WML_FACTORY.createP();
        addInlineRuns(p, text);
        return p;
    }

    private void addInlineRuns(P p, String text) {
        String[] parts = text.split("(?<=\\*\\*)|(?=\\*\\*)");
        boolean bold = false;
        for (String part : parts) {
            if (part.equals("**")) {
                bold = !bold;
            } else if (!part.isEmpty()) {
                p.getContent().add(makeRun(part, bold, false));
            }
        }
    }

    private R makeRun(String text, boolean bold, boolean italic) {
        R r = WML_FACTORY.createR();
        RPr rPr = WML_FACTORY.createRPr();
        if (bold) {
            BooleanDefaultTrue b = WML_FACTORY.createBooleanDefaultTrue();
            rPr.setB(b);
        }
        if (italic) {
            BooleanDefaultTrue i = WML_FACTORY.createBooleanDefaultTrue();
            rPr.setI(i);
        }
        r.setRPr(rPr);

        Text t = WML_FACTORY.createText();
        t.setValue(text);
        t.setSpace("preserve");
        r.getContent().add(t);
        return r;
    }
}