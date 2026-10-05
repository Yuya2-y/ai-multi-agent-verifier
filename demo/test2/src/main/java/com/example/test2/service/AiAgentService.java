package com.example.test2.service;

import com.example.test2.dto.AgentUsageSummaryDto;
import com.example.test2.dto.ApiResponseDto;
import com.example.test2.dto.ChatHistorySummaryDto;
import com.example.test2.dto.ChatResultDto;
import com.example.test2.entity.ChatHistory;
import com.example.test2.entity.UserAccount;
import com.example.test2.repository.ChatHistoryRepository;
import com.example.test2.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiAgentService {

    @Value("${gemini.api.key}")
    private String apiKey;

    // 大文字・小文字どちらの環境変数でも柔軟に読み込める設定に強化
    @Value("${groq.api.key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    @Value("${gemini.api.model:gemini-3.5-flash-lite}")
    private String geminiModel;

    @Value("${groq.api.model:openai/gpt-oss-120b}")
    private String groqModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;
    private final Executor aiTaskExecutor;
    
    // GeminiのURL
    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    // 正しいGroqの窓口（APIエンドポイント）URL
    private final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public AiAgentService(ChatHistoryRepository chatHistoryRepository, UserRepository userRepository, Executor aiTaskExecutor) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.userRepository = userRepository;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * 全ての会話履歴を取得（新しい順）
     */
    public List<ChatHistory> getAllChatHistory() {
        return chatHistoryRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ChatHistory> getChatHistoryForUser(String username) {
        return chatHistoryRepository.findByUserUsernameOrderByCreatedAtDesc(username);
    }

    public ChatHistory getChatHistoryById(Long id) {
        return chatHistoryRepository.findById(id).orElse(null);
    }

    public ChatHistory getChatHistoryByIdAndUsername(Long id, String username) {
        return chatHistoryRepository.findByIdAndUserUsername(id, username);
    }

    public ChatHistorySummaryDto getChatSummaryForUser(String username) {
        return chatHistoryRepository.findSummaryByUsername(username);
    }

    public List<AgentUsageSummaryDto> getAgentUsageSummaryForUser(String username) {
        return chatHistoryRepository.summarizeAgentUsageByUsername(username);
    }

    public ChatResultDto processMultiAgentChat(String userQuery) {
        return processMultiAgentChat(userQuery, null, null);
    }

    public ChatResultDto processMultiAgentChat(String userQuery, ChatHistory contextHistory) {
        return processMultiAgentChat(userQuery, contextHistory, null);
    }

    public ChatResultDto processMultiAgentChat(String userQuery, ChatHistory contextHistory, String username) {
        ApiResponseDto resultDto = new ApiResponseDto();
        ChatResultDto chatResultDto = new ChatResultDto();
        try {
            UserAccount user = null;
            if (username != null) {
                user = userRepository.findByUsername(username).orElse(null);
            }

            String initialPrompt = "あなたは親切な専門家です。質問に詳しく答えてください。";
            String inputText;
            if (contextHistory != null) {
                initialPrompt = "あなたは親切な専門家です。前回の会話を踏まえて、続きの質問に答えてください。";
                String previousLog = contextHistory.getConversationLog() != null ? contextHistory.getConversationLog() : "";
                inputText = "【前回までの会話】:\n" + previousLog + "\n\n" +
                            "【今回の追加質問】:\n" + userQuery;
            } else {
                inputText = userQuery;
            }

            AtomicReference<String> adoptedAgentRef = new AtomicReference<>("Unknown");
            String draft = callGeminiWithFallback(initialPrompt, inputText, false, adoptedAgentRef);
            resultDto.setDraft_answer(draft);

            String critiquePrompt = "【元の質問】:\n" + inputText + "\n\n【提出された回答】:\n" + draft;
            String critique = callGeminiWithFallback("あなたは厳格な検証官です。提出された回答にウソや矛盾がないか厳しく批判し、修正案を出してください。", critiquePrompt, false, adoptedAgentRef);
            resultDto.setCritique(critique);

            String scoringSystem = "あなたは最終まとめ役です。最初の回答と検証官の批判を元に、最も正確な【最終回答】を作成し、信頼性を以下の4項目（各25点満点）で厳格に採点し、合計の【信頼性（%）】を算出してください。出力は必ず以下のJSONフォーマット（キー名厳守）のみで返してください。\n{\"final_answer\": \"最終回答...\", \"confidence_score\": 85}";
            String scoringUser = "【最初の回答】:\n" + draft + "\n\n【検証官の批判】:\n" + critique;
            
            String jsonResponse = callGeminiWithFallback(scoringSystem, scoringUser, true, adoptedAgentRef);
            
            JsonNode root = objectMapper.readTree(jsonResponse);
            resultDto.setFinal_answer(root.path("final_answer").asText());
            resultDto.setConfidence_score(root.path("confidence_score").asInt());

            String adoptedAgent = adoptedAgentRef.get();

            // 会話履歴をDBに保存
            if (contextHistory == null) {
                ChatHistory chatHistory = new ChatHistory();
                chatHistory.setQuery(userQuery);
                chatHistory.setFinalAnswer(resultDto.getFinal_answer());
                chatHistory.setConfidenceScore(resultDto.getConfidence_score());
                chatHistory.setDraftAnswer(resultDto.getDraft_answer());
                chatHistory.setCritique(resultDto.getCritique());
                chatHistory.setSessionTitle(createSessionTitle(userQuery));
                chatHistory.setConversationLog(buildConversationLog(userQuery, resultDto, adoptedAgent));
                chatHistory.setAdoptedAgent(adoptedAgent);
                if (user != null) {
                    chatHistory.setUser(user);
                }
                chatHistoryRepository.save(chatHistory);
                chatResultDto.setChatHistory(chatHistory);
            } else {
                contextHistory.setQuery(userQuery);
                contextHistory.setFinalAnswer(resultDto.getFinal_answer());
                contextHistory.setConfidenceScore(resultDto.getConfidence_score());
                contextHistory.setDraftAnswer(resultDto.getDraft_answer());
                contextHistory.setCritique(resultDto.getCritique());
                contextHistory.setAdoptedAgent(adoptedAgent);
                if (contextHistory.getUser() == null && user != null) {
                    contextHistory.setUser(user);
                }
                String updatedLog = contextHistory.getConversationLog();
                if (updatedLog == null) {
                    updatedLog = "";
                }
                if (!updatedLog.isEmpty()) {
                    updatedLog += "\n\n";
                }
                updatedLog += buildConversationLog(userQuery, resultDto, adoptedAgent);
                contextHistory.setConversationLog(updatedLog);
                chatHistoryRepository.save(contextHistory);
                chatResultDto.setChatHistory(contextHistory);
            }

        } catch (Exception e) {
            resultDto.setFinal_answer("エラーが発生しました: " + e.getMessage());
            resultDto.setConfidence_score(0);
        }
        chatResultDto.setResult(resultDto);
        return chatResultDto;
    }

    /**
     * Geminiを呼び出すメソッド（429エラーや最大リトライ超過時にGroqへフォールバックする）
     */
    private String callGeminiWithFallback(String systemPrompt, String userPrompt, boolean forceJson) throws Exception {
        return callGeminiWithFallback(systemPrompt, userPrompt, forceJson, new AtomicReference<>("Unknown"));
    }

    private String callGeminiWithFallback(String systemPrompt, String userPrompt, boolean forceJson, AtomicReference<String> adoptedAgentRef) throws Exception {
        // Gemini APIキーが未設定または空文字の場合は直接Groqへフォールバックする
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("⚠️ Gemini APIキーが未設定です。直接Groq (Llama 3.3) へフォールバックします。");
            adoptedAgentRef.set("Groq");
            return callGroq(systemPrompt, userPrompt, forceJson);
        }

        try {
            return callFirstAvailableAgent(systemPrompt, userPrompt, forceJson, adoptedAgentRef);
        } catch (Exception e) {
            System.out.println("⚠️ Gemini/Groqの同時実行に失敗しました。再試行としてGroqへフォールバックします。理由: " + e.getMessage());
            adoptedAgentRef.set("Groq");
            return callGroq(systemPrompt, userPrompt, forceJson);
        }
    }

    private String callFirstAvailableAgent(String systemPrompt, String userPrompt, boolean forceJson, AtomicReference<String> adoptedAgentRef) throws Exception {
        CompletableFuture<String> geminiFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return callGemini(systemPrompt, userPrompt, forceJson);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, aiTaskExecutor);

        CompletableFuture<String> groqFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return callGroq(systemPrompt, userPrompt, forceJson);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, aiTaskExecutor);

        CompletableFuture<String> firstSuccessful = new CompletableFuture<>();
        AtomicInteger remaining = new AtomicInteger(2);

        geminiFuture.whenComplete((result, error) -> {
            if (error == null) {
                if (!firstSuccessful.isDone()) {
                    adoptedAgentRef.set("Gemini");
                    firstSuccessful.complete(result);
                }
            } else if (remaining.decrementAndGet() == 0) {
                firstSuccessful.completeExceptionally(error.getCause() != null ? error.getCause() : error);
            }
        });

        groqFuture.whenComplete((result, error) -> {
            if (error == null) {
                if (!firstSuccessful.isDone()) {
                    adoptedAgentRef.set("Groq");
                    firstSuccessful.complete(result);
                }
            } else if (remaining.decrementAndGet() == 0) {
                firstSuccessful.completeExceptionally(error.getCause() != null ? error.getCause() : error);
            }
        });

        try {
            String response = firstSuccessful.get(60, TimeUnit.SECONDS);
            geminiFuture.cancel(true);
            groqFuture.cancel(true);
            return response;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new Exception(cause);
        }
    }

    /**
     * 【オリジナル】GeminiAPI呼び出し処理（リトライロジック含む）
     */
    private String callGemini(String systemPrompt, String userPrompt, boolean forceJson) throws Exception {
        String completeUrl = API_URL + geminiModel + ":generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        String totalPrompt = "【前提指示】\n" + systemPrompt + "\n\n【入力内容】\n" + userPrompt;

        Map<String, Object> textPart = Map.of("text", totalPrompt);
        Map<String, Object> contentMap = Map.of("parts", List.of(textPart));
        requestBody.put("contents", List.of(contentMap));

        if (forceJson) {
            Map<String, Object> generationConfig = Map.of("responseMimeType", "application/json");
            requestBody.put("generationConfig", generationConfig);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(completeUrl, entity, String.class);

                JsonNode resRoot = objectMapper.readTree(response.getBody());
                String aiText = resRoot.path("candidates").get(0)
                                       .path("content").path("parts").get(0)
                                       .path("text").asText();

                if (forceJson) {
                    aiText = aiText.replaceAll("```json", "").replaceAll("```", "").trim();
                }
                return aiText;

            } catch (org.springframework.web.client.HttpClientErrorException e) {
                // 🔴 変更ポイント: 429（無料枠制限）エラーの時は、40秒待たずに即座に例外を投げてフォールバックさせる
                if (e.getStatusCode().value() == 429) {
                    System.out.println("⚠️ Geminiが429エラーを返しました。待機せず即座にフォールバックします。");
                    throw e;
                } else if (i < maxRetries - 1) {
                    // その他のエラーの場合は、一応少し待ってリトライ
                    System.out.println("一時的なエラー。2秒後にリトライします... (" + (i + 1) + "回目)");
                    Thread.sleep(2000);
                } else {
                    throw e;
                }
            }
        }
        throw new Exception("Geminiの最大リトライ回数を超えました");
    }

    /**
     * 【新規追加】Groq (Llama 3.3 70b) を呼び出す処理
     */
    private String callGroq(String systemPrompt, String userPrompt, boolean forceJson) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + groqApiKey.trim()); // 確実に認証ヘッダーをセット

        // リクエストボディの作成（OpenAI互換フォーマット）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", groqModel);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        requestBody.put("messages", messages);

        // JSON出力を強制する場合の設定
        if (forceJson) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_API_URL, entity, String.class);
            JsonNode resRoot = objectMapper.readTree(response.getBody());
            
            // Groqのレスポンスからテキストを抽出
            String aiText = resRoot.path("choices").get(0)
                                   .path("message").path("content").asText();

            if (forceJson) {
                aiText = aiText.replaceAll("```json", "").replaceAll("```", "").trim();
            }
            return aiText;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("❌ Groq API エラーレスポンス: " + e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Groqの呼び出しに失敗しました: " + e.getMessage());
            throw e;
        }
    }

    private String createSessionTitle(String initialQuery) {
        if (initialQuery == null) {
            return "新しい会話";
        }
        if (initialQuery.length() <= 36) {
            return initialQuery;
        }
        return initialQuery.substring(0, 36) + "...";
    }

    private String buildConversationLog(String userQuery, ApiResponseDto resultDto, String adoptedAgent) {
        if (adoptedAgent == null || adoptedAgent.trim().isEmpty()) {
            adoptedAgent = "Unknown";
        }
        return "採用AI: " + adoptedAgent + "\nUser: " + userQuery + "\nAssistant: " + resultDto.getFinal_answer();
    }
}
