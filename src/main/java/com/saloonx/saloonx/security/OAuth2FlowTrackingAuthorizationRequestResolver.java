package com.saloonx.saloonx.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class OAuth2FlowTrackingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String AUTHORIZATION_BASE_URI = "/oauth2/init";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final HttpSession session;

    public OAuth2FlowTrackingAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository, HttpSession session) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, AUTHORIZATION_BASE_URI);
        this.session = session;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        trackFlow(request);
        return delegate.resolve(request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        trackFlow(request);
        return delegate.resolve(request, clientRegistrationId);
    }

    private void trackFlow(HttpServletRequest request) {
        String flow = request.getParameter("flow");
        String normalizedFlow = switch (flow == null ? "" : flow.trim().toLowerCase()) {
            case "beautician" -> "beautician";
            case "customer" -> "customer";
            default -> "login";
        };
        session.setAttribute("oauth_user_role", "beautician".equals(normalizedFlow) ? "BEAUTICIAN" : "CUSTOMER");
        session.setAttribute("oauth_entry_flow", normalizedFlow);
    }
}
