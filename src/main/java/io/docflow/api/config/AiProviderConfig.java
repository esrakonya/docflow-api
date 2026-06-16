package io.docflow.api.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiProviderConfig {

    @Bean
    public ChatClient documentChatClient(
            @Value("${docflow.ai.provider:gemini}") String provider,
            AnthropicChatModel anthropicChatModel,
            GoogleGenAiChatModel googleGenAiChatModel
    ) {
        return switch (provider.toLowerCase()) {
            case "anthropic", "claude" -> ChatClient.create(anthropicChatModel);
            case "gemini", "google" -> ChatClient.create(googleGenAiChatModel);
            default -> throw new IllegalArgumentException(
                    "Bilinmeyen docflow.ai.provider değeri: '" + provider
                            + "'. Geçerli değerler: 'anthropic', 'gemini'.");
        };
    }
}
