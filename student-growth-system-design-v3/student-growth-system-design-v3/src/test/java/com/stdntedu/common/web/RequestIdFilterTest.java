package com.stdntedu.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class RequestIdFilterTest {
    @Mock FilterChain chain;

    @Test
    void preservesIncomingRequestIdAndResponseHeader() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "client-request");
        var response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-request");
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo("client-request");
        verify(chain).doFilter(request, response);
    }

    @Test
    void createsRequestIdWhenMissing() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE))
                .isEqualTo(response.getHeader(RequestIdFilter.HEADER));
        verify(chain).doFilter(request, response);
    }
}
