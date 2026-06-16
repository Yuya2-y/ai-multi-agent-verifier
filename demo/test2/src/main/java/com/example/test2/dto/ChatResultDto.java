package com.example.test2.dto;

import com.example.test2.entity.ChatHistory;
import lombok.Data;

@Data
public class ChatResultDto {
    private ApiResponseDto result;
    private ChatHistory chatHistory;
}
