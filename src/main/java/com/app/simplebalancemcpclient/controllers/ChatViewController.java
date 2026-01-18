package com.app.simplebalancemcpclient.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChatViewController {

    @GetMapping("/")
    public String chatPage() {
        return "chat";
    }
}
