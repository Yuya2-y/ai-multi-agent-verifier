package com.example.test2.controller;

import com.example.test2.dto.ApiResponseDto;
import com.example.test2.entity.ChatHistory;
import com.example.test2.service.AiAgentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ChatController {

    private final AiAgentService aiAgentService;

    public ChatController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("chatHistory", chatHistory);
        return "index";
    }

    @PostMapping("/chat")
    public String chat(@RequestParam("query") String query, Model model) {
        if (query == null || query.trim().isEmpty()) {
            return "redirect:/";
        }
        ApiResponseDto result = aiAgentService.processMultiAgentChat(query);
        model.addAttribute("query", query);
        model.addAttribute("result", result);
        
        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("chatHistory", chatHistory);
        return "index";
    }
}
