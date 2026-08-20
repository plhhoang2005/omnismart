package vn.omnismart.common.api;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ApiSecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> write(
                request, response, HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "Authentication is required");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> write(
                request, response, HttpStatus.FORBIDDEN,
                "ACCESS_DENIED", "The request is not permitted");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws IOException {
        Object requestId = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE_NAME);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                code,
                message,
                List.of(),
                requestId == null ? null : requestId.toString(),
                request.getRequestURI(),
                OffsetDateTime.now()));
    }
}
