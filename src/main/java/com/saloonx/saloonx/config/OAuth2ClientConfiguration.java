package com.saloonx.saloonx.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.ClientRegistration.Builder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(OAuth2ProviderProperties.class)
public class OAuth2ClientConfiguration {

    @Bean
    public DynamicClientRegistrationRepository clientRegistrationRepository(OAuth2ProviderProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();
        for (Map.Entry<String, OAuth2ProviderProperties.ProviderSettings> entry : properties.getProviders().entrySet()) {
            ClientRegistration registration = buildRegistration(entry.getKey(), entry.getValue());
            if (registration != null) {
                registrations.add(registration);
            }
        }
        return new DynamicClientRegistrationRepository(registrations);
    }

    private ClientRegistration buildRegistration(String registrationId, OAuth2ProviderProperties.ProviderSettings settings) {
        if (settings == null || !settings.isConfigured()) {
            return null;
        }

        String id = registrationId.toLowerCase();
        Builder builder = switch (id) {
            case "google" -> withGoogleBuilder(id, settings);
            case "facebook" -> withFacebookBuilder(id, settings);
            case "apple" -> withAppleBuilder(id, settings);
            default -> withCustomBuilder(id, settings);
        };
        return builder == null ? null : builder.build();
    }

    private Builder withGoogleBuilder(String registrationId, OAuth2ProviderProperties.ProviderSettings settings) {
        Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs");

        builder.clientId(settings.getClientId());
        builder.clientSecret(settings.getClientSecret());
        builder.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}");
        builder.scope(resolveScopes(settings));
        builder.clientName(settings.getClientName() == null || settings.getClientName().isBlank() ? "Google" : settings.getClientName());
        return builder;
    }

    private Builder withFacebookBuilder(String registrationId, OAuth2ProviderProperties.ProviderSettings settings) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(settings.getClientId())
                .clientSecret(settings.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(resolveScopes(settings))
                .authorizationUri("https://www.facebook.com/v19.0/dialog/oauth")
                .tokenUri("https://graph.facebook.com/v19.0/oauth/access_token")
                .userInfoUri("https://graph.facebook.com/me?fields=id,name,email")
                .userNameAttributeName("id")
                .clientName(settings.getClientName() == null || settings.getClientName().isBlank() ? "Facebook" : settings.getClientName());
    }

    private Builder withAppleBuilder(String registrationId, OAuth2ProviderProperties.ProviderSettings settings) {
        Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .tokenUri("https://appleid.apple.com/auth/token")
                .jwkSetUri("https://appleid.apple.com/auth/keys")
                .userNameAttributeName("sub");

        builder.clientId(settings.getClientId());
        builder.clientSecret(settings.getClientSecret());
        builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        builder.scope(resolveScopes(settings));
        builder.clientName(settings.getClientName() == null || settings.getClientName().isBlank() ? "Apple" : settings.getClientName());
        return builder;
    }

    private Builder withCustomBuilder(String registrationId, OAuth2ProviderProperties.ProviderSettings settings) {
        if (isBlank(settings.getAuthorizationUri()) || isBlank(settings.getTokenUri())) {
            return null;
        }

        Builder builder = ClientRegistration.withRegistrationId(registrationId)
                .clientId(settings.getClientId())
                .clientSecret(settings.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri(settings.getAuthorizationUri())
                .tokenUri(settings.getTokenUri())
                .clientName(settings.getClientName() == null || settings.getClientName().isBlank() ? registrationId : settings.getClientName())
                .scope(resolveScopes(settings));

        if (!isBlank(settings.getUserInfoUri())) {
            builder.userInfoUri(settings.getUserInfoUri());
        }
        if (!isBlank(settings.getUserNameAttribute())) {
            builder.userNameAttributeName(settings.getUserNameAttribute());
        }
        return builder;
    }

    private List<String> resolveScopes(OAuth2ProviderProperties.ProviderSettings settings) {
        return settings.getScope() == null || settings.getScope().isEmpty()
                ? List.of("openid", "profile", "email")
                : settings.getScope();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
