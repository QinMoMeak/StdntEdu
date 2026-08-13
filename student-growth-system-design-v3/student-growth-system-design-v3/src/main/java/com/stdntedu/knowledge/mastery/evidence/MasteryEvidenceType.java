package com.stdntedu.knowledge.mastery.evidence;

public enum MasteryEvidenceType {
    EXAM(0), PRACTICE(1), REVIEW(2);

    private final int priority;

    MasteryEvidenceType(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
