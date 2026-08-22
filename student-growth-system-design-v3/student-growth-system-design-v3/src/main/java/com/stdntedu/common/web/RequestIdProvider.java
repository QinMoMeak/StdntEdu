package com.stdntedu.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestIdProvider {
    public String current() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
            return requestId instanceof String value ? value : null;
        }
        return null;
    }
}
