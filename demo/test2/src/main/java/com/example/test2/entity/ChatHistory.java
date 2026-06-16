package com.example.test2.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(columnDefinition = "TEXT")
    private String query;
    
    @Column(name = "final_answer", columnDefinition = "TEXT")
    private String finalAnswer;
    
    private Integer confidenceScore;
    
    @Column(name = "draft_answer", columnDefinition = "TEXT")
    private String draftAnswer;
    
    @Column(columnDefinition = "TEXT")
    private String critique;
    
    @Column(columnDefinition = "TEXT")
    private String sessionTitle;
    
    @Column(columnDefinition = "TEXT")
    private String conversationLog;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
