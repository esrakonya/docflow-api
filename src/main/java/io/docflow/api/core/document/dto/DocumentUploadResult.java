package io.docflow.api.core.document.dto;

/**
 * Internal service result for document uploads. It keeps quota reservation in
 * the service layer while allowing the web layer to expose the remaining
 * quota through response headers without reserving it a second time.
 */
public record DocumentUploadResult<T>(T upload, int remainingQuota) {
}
