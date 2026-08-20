package com.stdntedu.ai.analysis.generation;

public class GenerationFailure extends RuntimeException {
    private final String code;

    public GenerationFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
