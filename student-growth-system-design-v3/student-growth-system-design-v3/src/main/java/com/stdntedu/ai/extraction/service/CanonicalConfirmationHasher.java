package com.stdntedu.ai.extraction.service;

import java.util.Comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stdntedu.ai.common.CanonicalJsonHasher;
import com.stdntedu.generated.model.AiConfirm;
import com.stdntedu.generated.model.AiConfirmItem;
import com.stdntedu.generated.model.KnowledgeLink;
import org.springframework.stereotype.Component;

@Component
public class CanonicalConfirmationHasher {
    private final ObjectMapper objectMapper;
    private final CanonicalJsonHasher hasher;

    public CanonicalConfirmationHasher(ObjectMapper objectMapper, CanonicalJsonHasher hasher) {
        this.objectMapper = objectMapper;
        this.hasher = hasher;
    }

    public String hash(AiConfirm request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("atomic", Boolean.TRUE.equals(request.getAtomic()));
        ArrayNode questions = root.putArray("questions");
        request.getQuestions().stream().sorted(Comparator.comparing(AiConfirmItem::getTemporaryQuestionId))
                .forEach(item -> questions.add(item(item)));
        return hasher.hash(root);
    }

    private ObjectNode item(AiConfirmItem item) {
        ObjectNode node = objectMapper.createObjectNode();
        text(node, "temporaryQuestionId", item.getTemporaryQuestionId());
        node.put("save", Boolean.TRUE.equals(item.getSave()));
        text(node, "questionType", item.getQuestionType());
        text(node, "questionText", item.getQuestionText());
        text(node, "studentAnswer", item.getStudentAnswer());
        text(node, "correctAnswer", item.getCorrectAnswer());
        text(node, "analysisText", item.getAnalysisText());
        text(node, "errorType", item.getErrorType());
        if (item.getDifficulty() == null) node.putNull("difficulty"); else node.put("difficulty", item.getDifficulty());
        if (item.getOccurredDate() == null) node.putNull("occurredDate");
        else node.put("occurredDate", item.getOccurredDate().toString());
        ArrayNode points = node.putArray("knowledgePoints");
        item.getKnowledgePoints().stream().sorted(Comparator.comparing(KnowledgeLink::getKnowledgeId))
                .forEach(point -> {
                    ObjectNode link = points.addObject();
                    link.put("knowledgeId", point.getKnowledgeId());
                    link.put("primary", Boolean.TRUE.equals(point.getPrimary()));
                    if (point.getConfidence() == null) link.putNull("confidence");
                    else link.put("confidence", point.getConfidence());
                });
        return node;
    }

    private void text(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
}
