package io.docflow.api.infrastructure.constant;

public class MessageConstants {
    // Error Messages
    public static final String QUOTA_EXCEEDED = "Monthly quota exceeded for this client.";
    public static final String INVALID_FILE_TYPE = "Invalid file type. Only PDF, PNG and JPEG are allowed.";
    public static final String FILE_SIZE_EXCEEDED = "File size exceeds the maximum limit.";
    public static final String DOCUMENT_NOT_FOUND = "Document not found with ID: ";

    // Log Messages
    public static final String WEBHOOK_SUCCESS = "Webhook delivery successful for client: {}";
    public static final String WEBHOOK_FAILURE = "Webhook delivery failed for client: {}. Error: {}";
    public static final String CLEANUP_STARTED = "File cleanup process started.";
    public static final String CLEANUP_FINISHED = "File cleanup process finished. Deleted {} files.";
}
