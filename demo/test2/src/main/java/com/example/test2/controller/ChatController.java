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
        model.addAttribute("query", "");
        model.addAttribute("selectedHistory", null);
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
        model.addAttribute("selectedHistory", null);
        
        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("chatHistory", chatHistory);
        return "index";
    }

    @GetMapping("/history/{id}")
    public String viewHistory(@PathVariable Long id, Model model) {
        ChatHistory selectedHistory = aiAgentService.getChatHistoryById(id);
        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("selectedHistory", selectedHistory);
        model.addAttribute("chatHistory", chatHistory);
        model.addAttribute("query", selectedHistory != null ? selectedHistory.getQuery() : "");
        return "index";
    }
}
