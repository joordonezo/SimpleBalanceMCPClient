package com.app.simplebalancemcpclient.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2ClientCredentialsSyncHttpRequestCustomizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.time.Duration;
import java.util.Arrays;

@Configuration
class McpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider toolCallbackProvider) {
        try {
            Arrays.stream(toolCallbackProvider.getToolCallbacks()).forEach(toolCallback ->
                    log.debug("Tool Callback Class: {}", toolCallback.getToolDefinition())
            );
        } catch (Exception e) {
            log.warn("Could not load MCP tool callbacks at startup (will be loaded on-demand): {}", e.getMessage());
        }
        return builder
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    @Bean
    McpSyncClientCustomizer syncClientCustomizer() {
        return (name, syncSpec) ->
                syncSpec.transportContextProvider(
                        new AuthenticationMcpTransportContextProvider()
                ).requestTimeout(Duration.ofSeconds(30));
    }

    @Bean
    AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)
        );
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }

    @Bean
    McpSyncHttpClientRequestCustomizer requestCustomizer(
            AuthorizedClientServiceOAuth2AuthorizedClientManager clientManager
    ) {
        return new OAuth2ClientCredentialsSyncHttpRequestCustomizer(
                clientManager,
                "mcp-client"
        );
    }
}
