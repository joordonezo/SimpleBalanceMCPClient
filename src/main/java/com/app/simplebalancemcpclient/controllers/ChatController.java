package com.app.simplebalancemcpclient.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequiredArgsConstructor
public class ChatController {
    private final ChatClient chatClient;

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message) {

        AtomicReference<String> previousChunk = new AtomicReference<>("");

        return chatClient
                .prompt()
                .user(message)
                .stream()
                .content()
                .windowUntil(chunk -> {
                    String prev = previousChunk.get();
                    previousChunk.set(chunk);

                    if (chunk.equals(".") && prev.matches(".*\\d$")) {
                        return false;
                    }

                    if (prev.equals(".") && chunk.matches("^\\d.*")) {
                        return false;
                    }

                    if (chunk.endsWith(" ") || chunk.endsWith("\n")) {
                        return true;
                    }

                    if (chunk.matches(".*[,;:!?]$")) {
                        return true;
                    }

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
