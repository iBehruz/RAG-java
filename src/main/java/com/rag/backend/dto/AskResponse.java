package com.rag.backend.dto;

public record AskResponse(    String question,
                                       String answer,
                                       String rating) {

}
