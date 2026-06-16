package com.example.test2.controller;

import com.example.test2.controller.AuthController;
import com.example.test2.dto.ApiResponseDto;
import com.example.test2.entity.ChatHistory;
import com.example.test2.service.AiAgentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
public class ChatController {

    private final AiAgentService aiAgentService;

    public ChatController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @GetMapping("/")
    public String index(HttpSession session, Model model) {
        if (AuthController.getSessionUser(session) == null) {
            return "redirect:/login";
        }
        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("chatHistory", chatHistory);
        model.addAttribute("query", "");
        model.addAttribute("selectedHistory", null);
        model.addAttribute("canInput", true);
        model.addAttribute("currentUser", AuthController.getSessionUser(session));
        return "index";
    }

    @GetMapping("/new")
    public String newConversation(HttpSession session, Model model) {
        if (AuthController.getSessionUser(session) == null) {
            return "redirect:/login";
        }
        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("chatHistory", chatHistory);
        model.addAttribute("query", "");
        model.addAttribute("result", null);
        model.addAttribute("selectedHistory", null);
        model.addAttribute("canInput", true);
        return "index";
    }

    @PostMapping("/chat")
    public String chat(@RequestParam("query") String query,
                       @RequestParam(value = "historyId", required = false) Long historyId,
                       HttpSession session,
                       Model model) {
        if (AuthController.getSessionUser(session) == null) {
            return "redirect:/login";
        }
        if (query == null || query.trim().isEmpty()) {
            return "redirect:/";
        }

        ChatHistory selectedHistory = null;
        if (historyId != null) {
            selectedHistory = aiAgentService.getChatHistoryById(historyId);
        }

        var chatResult = aiAgentService.processMultiAgentChat(query, selectedHistory);
        ApiResponseDto result = chatResult.getResult();
        selectedHistory = chatResult.getChatHistory();

        model.addAttribute("query", query);
        model.addAttribute("result", result);
        model.addAttribute("selectedHistory", selectedHistory);
        model.addAttribute("canInput", true);
        model.addAttribute("currentUser", AuthController.getSessionUser(session));

        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("chatHistory", chatHistory);
        return "index";
    }

    @GetMapping("/history/{id}")
    public String viewHistory(@PathVariable Long id, HttpSession session, Model model) {
        if (AuthController.getSessionUser(session) == null) {
            return "redirect:/login";
        }
        ChatHistory selectedHistory = aiAgentService.getChatHistoryById(id);
        if (selectedHistory == null) {
            return "redirect:/";
        }
        List<ChatHistory> chatHistory = aiAgentService.getAllChatHistory();
        model.addAttribute("selectedHistory", selectedHistory);
        model.addAttribute("chatHistory", chatHistory);
        model.addAttribute("query", "");
        model.addAttribute("canInput", true);
        model.addAttribute("currentUser", AuthController.getSessionUser(session));
        return "index";
    }
}
