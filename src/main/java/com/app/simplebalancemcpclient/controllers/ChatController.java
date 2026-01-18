package com.app.simplebalancemcpclient.controllers;

import com.app.simplebalancemcpclient.records.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@RestController
//@RequiredArgsConstructor
public class ChatController {
    private static final Logger log =  LoggerFactory.getLogger(ChatController.class);
    private final ChatClient  chatClient;
    //private final ToolCallbackProvider tools;

    public ChatController(ChatClient.Builder builder, ToolCallbackProvider toolCallbackProvider) {
        Arrays.stream(toolCallbackProvider.getToolCallbacks()).forEach( toolCallback -> {
            log.info("Tool Callback Class: {}", toolCallback.getToolDefinition());
        });
        this.chatClient = builder
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message) {

        AtomicReference<String> previousChunk = new AtomicReference<>("");

        return chatClient
                .prompt()
                .user(message)
                .stream()
                .content()
                // Acumular chunks hasta encontrar espacios o puntuación (pero NO punto decimal)
                .windowUntil(chunk -> {
                    String prev = previousChunk.get();
                    previousChunk.set(chunk);

                    // Si el chunk es solo un punto y el anterior era un número, NO cortar
                    if (chunk.equals(".") && prev.matches(".*\\d$")) {
                        return false;
                    }

                    // Si el anterior era punto y este es un número, NO cortar
                    if (prev.equals(".") && chunk.matches("^\\d.*")) {
                        return false;
                    }

                    // Cortar en espacios o saltos de línea
                    if (chunk.endsWith(" ") || chunk.endsWith("\n")) {
                        return true;
                    }

                    // Cortar en puntuación que NO sea punto decimal
                    // Solo cortar en punto si NO está entre números
                    if (chunk.matches(".*[,;:!?]$")) {
                        return true;
                    }

                    // Cortar en punto solo si NO viene después de un número
                    if (chunk.endsWith(".") && !prev.matches(".*\\d$")) {
                        return true;
                    }

                    return false;
                })
                .flatMap(window -> window.reduce("", (acc, chunk) -> acc + chunk))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .onErrorResume(e -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .data("Error: " + e.getMessage())
                                .build()
                ));
    }

}
