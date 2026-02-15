package com.legaldocsgpt.shared.exception;

import lombok.Getter;

@Getter
public class ThirdPartyApiException extends BaseBusinessException {
    private final String providerName; // e.g., "OpenAI"

    public ThirdPartyApiException(String providerName, String message) {
        super(GlobalErrorCode.AI_PROVIDER_UNAVAILABLE,
                String.format("Error communicating with %s: %s", providerName, message));
        this.providerName = providerName;
    }
}