package com.legaldocsgpt.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditorConfigResponse {

    private DocumentConfig document;

    private EditorConfig editorConfig;

    private String token;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentConfig {

        /**
         * Unique document key used by OnlyOffice to cache collaborative sessions.
         * Use jobId — must change whenever the document content changes so OnlyOffice
         * re-fetches the latest version rather than serving a stale cache.
         * Max 128 URL-safe characters.
         */
        private String key;

        /** Display title shown in the editor toolbar. */
        private String title;

        /**
         * URL from which OnlyOffice Document Server downloads the file to edit.
         * Must be reachable from the onlyoffice-ds container, NOT from the browser.
         * Example: "http://storage-service:8084/download-raw?key=<jobId>.docx"
         */
        private String url;

        /**
         * MIME file type. Use "docx" for Word documents.
         * OnlyOffice uses this to select the appropriate editor module.
         */
        private String fileType;

        /** Permissions granted to the editing session. */
        private Permissions permissions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Permissions {
        /** Allow the user to edit the document. Set false for read-only preview. */
        private boolean edit;

        /** Allow downloading the file from within the editor. */
        private boolean download;

        /** Allow printing from within the editor. */
        private boolean print;
    }

    // -------------------------------------------------------------------------
    // Nested: editorConfig
    // -------------------------------------------------------------------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EditorConfig {

        /**
         * URL that OnlyOffice Document Server POSTs save events to.
         * Must be reachable from the onlyoffice-ds container.
         * Append the signed edit token as a query param so storage-service
         * can verify ownership without a user session:
         * "http://storage-service:8084/callback?token=<signedEditToken>"
         */
        private String callbackUrl;

        /**
         * Editor display language (IETF BCP 47).
         * Example: "en-US", "de-DE"
         */
        private String lang;

        /**
         * Editor mode: "edit" or "view".
         * Use "view" when the document is pending or belongs to another user.
         */
        private String mode;

        /** Info about the currently logged-in user shown in the editor UI. */
        private User user;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        /**
         * Unique user identifier passed to OnlyOffice for collaborative session tracking.
         * Use the userId from your auth system.
         */
        private String id;

        /** Display name shown on cursors and comments in collaborative mode. */
        private String name;
    }
}