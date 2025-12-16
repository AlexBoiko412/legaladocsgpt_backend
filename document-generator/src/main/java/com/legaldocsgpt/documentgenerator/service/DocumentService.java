package com.legaldocsgpt.documentgenerator.service;

import com.legaldocsgpt.documentgenerator.dto.GenerateRequest;
import com.legaldocsgpt.documentgenerator.dto.GenerateResponse;
import com.legaldocsgpt.documentgenerator.dto.TemplateInfo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    public GenerateResponse generateDocument(GenerateRequest request) {
        // mock
        return new GenerateResponse("/files/" + request.getTemplateId() + "." + request.getFormat().toLowerCase());
    }

    public List<TemplateInfo> getAvailableTemplates() {
        return List.of(
                new TemplateInfo("contract_sale_v1", "Contract of Sale",
                        List.of("sellerName", "buyerName", "price", "date")),
                new TemplateInfo("nda_v1", "Non-Disclosure Agreement",
                        List.of("partyA", "partyB", "effectiveDate"))
        );
    }

    public GenerateResponse aiGenerate(String description, String format) {
        // integration with ai api
        return new GenerateResponse("/files/generated_" + System.currentTimeMillis() + "." + format.toLowerCase());
    }
}