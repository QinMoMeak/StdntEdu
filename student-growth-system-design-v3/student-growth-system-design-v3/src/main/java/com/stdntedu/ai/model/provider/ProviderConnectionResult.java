package com.stdntedu.ai.model.provider;

public record ProviderConnectionResult(boolean success, String errorCode, String message, long latencyMs) { }
