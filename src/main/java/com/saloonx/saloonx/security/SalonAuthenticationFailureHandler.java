package com.saloonx.saloonx.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class SalonAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        String flow = session == null ? "login" : String.valueOf(session.getAttribute("oauth_entry_flow"));
        String path = switch (flow == null ? "login" : flow) {
            case "beautician" -> "/beautician-signup";
            case "customer" -> "/signup";
            default -> "/login";
        };

        if (session != null) {
            session.removeAttribute("oauth_user_role");
            session.removeAttribute("oauth_entry_flow");
        }

        String queryKey = request.getRequestURI().contains("/oauth2/") ? "oauthError" : "error";
        String target = UriComponentsBuilder.fromPath(path)
                .queryParam(queryKey)
                .build()
                .toUriString();
        response.sendRedirect(target);
    }
}
