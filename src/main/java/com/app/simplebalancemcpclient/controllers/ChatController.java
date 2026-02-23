package com.app.simplebalancemcpclient.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message) {
        log.debug("Received chat stream request with message: {}", message);

        // Usamos call() sincrono en lugar de stream() para evitar que las tool calls
        // del MCP SYNC client hagan block() en un hilo Reactor parallel (no permitido).
        // Envolvemos en Flux.defer + subscribeOn(boundedElastic) para que la llamada
        // bloqueante se ejecute en un hilo que SI permite bloqueo y no bloquee el event loop.
        return Flux.<ServerSentEvent<String>>defer(() -> {
            try {
                String response = chatClient
                        .prompt()
                        .user(message)
                        .call()
                        .content();

                log.debug("Chat response received: {} chars", response != null ? response.length() : 0);

                if (response == null || response.isEmpty()) {
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data("No se recibio respuesta del modelo.")
                            .build());
                }

                return Flux.just(ServerSentEvent.<String>builder()
                        .data(response)
                        .build());

            } catch (Exception e) {
                log.error("Error en chat call: {}", e.getMessage(), e);
                String errorMsg = extractUserFriendlyError(e);
                return Flux.just(ServerSentEvent.<String>builder()
                        .data(errorMsg)
                        .build());
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.error("Error en chat stream: {}", e.getMessage(), e);
            String errorMsg = extractUserFriendlyError(e);
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .data(errorMsg)
                            .build()
            );
        });
    }

    private String extractUserFriendlyError(Throwable e) {
        String fullChain = getFullErrorChain(e).toLowerCase();
        if (fullChain.contains("429") || fullChain.contains("too many requests") || fullChain.contains("rate limit")) {
            return "Error: Demasiadas peticiones al servidor de IA. Por favor espera unos segundos e intenta de nuevo.";
        }
        if (fullChain.contains("connection refused")) {
            return "Error: No se pudo conectar con el servidor MCP. Verifica que este corriendo.";
        }
        if (fullChain.contains("dummyevent") || fullChain.contains("failed to send message")) {
            return "Error: Se perdio la conexion con el servidor MCP. Reintentando no fue posible restablecer la sesion. Por favor reinicia la aplicacion.";
        }
        if (fullChain.contains("failed to initialize")) {
            return "Error: No se pudo inicializar la conexion con el servidor MCP. Verifica que el servidor MCP y el servidor de autorizacion esten corriendo.";
        }
        return "Error: " + getRootCauseMessage(e);
    }

    private String getFullErrorChain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null) {
                sb.append(cause.getMessage()).append(" | ");
            }
            cause = cause.getCause() != cause ? cause.getCause() : null;
        }
        return sb.toString();
    }

    private String getRootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
