package com.app.simplebalancemcpclient.controllers;

import com.app.simplebalancemcpclient.records.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Arrays;

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
    public Flux<String> chatStream(@RequestParam String message) {

        return chatClient
                .prompt()
                .user(message)
                .stream()
                .content();
    }

}
