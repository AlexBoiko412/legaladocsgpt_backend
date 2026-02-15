package com.legaldocsgpt.shared.config;

import com.legaldocsgpt.shared.context.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeignUserContextInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        String userId = UserContextHolder.getUserId();
        if (userId != null) {
            template.header("X-User-Id", userId);
        }
    }
}