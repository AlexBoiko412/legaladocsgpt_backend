package com.legaldocsgpt.shared.services;

import com.legaldocsgpt.shared.dto.EditorConfigResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "onlyoffice.jwt.secret")
public class OnlyOfficeJwtService {

    private final SecretKey key;

    public OnlyOfficeJwtService(@Value("${onlyoffice.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }
    public String buildToken(EditorConfigResponse config) {
        Map<String, Object> payload = buildPayload(config);

        return Jwts.builder()
                .claims(payload)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private Map<String, Object> buildPayload(EditorConfigResponse config) {
        EditorConfigResponse.DocumentConfig doc = config.getDocument();
        EditorConfigResponse.EditorConfig editor = config.getEditorConfig();

        Map<String, Object> permissions = new HashMap<>();
        permissions.put("edit", doc.getPermissions().isEdit());
        permissions.put("download", doc.getPermissions().isDownload());
        permissions.put("print", doc.getPermissions().isPrint());

        Map<String, Object> document = new HashMap<>();
        document.put("fileType", doc.getFileType());
        document.put("key", doc.getKey());
        document.put("title", doc.getTitle());
        document.put("url", doc.getUrl());
        document.put("permissions", permissions);

        Map<String, Object> user = new HashMap<>();
        user.put("id", editor.getUser().getId());
        user.put("name", editor.getUser().getName());

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("callbackUrl", editor.getCallbackUrl());
        editorConfig.put("lang", editor.getLang());
        editorConfig.put("mode", editor.getMode());
        editorConfig.put("user", user);

        Map<String, Object> payload = new HashMap<>();
        payload.put("document", document);
        payload.put("editorConfig", editorConfig);

        return payload;
    }
}