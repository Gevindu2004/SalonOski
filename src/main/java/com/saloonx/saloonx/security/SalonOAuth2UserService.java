package com.saloonx.saloonx.security;

import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SalonOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserService userService;
    private final HttpSession session;

    public SalonOAuth2UserService(UserService userService, HttpSession session) {
        this.userService = userService;
        this.session = session;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = delegate.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        String requestedRole = String.valueOf(session.getAttribute("oauth_user_role"));
        String email = stringAttribute(oauthUser, "email");
        String name = stringAttribute(oauthUser, "name");
        String providerUserId = resolveProviderUserId(oauthUser);

        User user;
        try {
            user = userService.provisionOAuthUser(provider, providerUserId, email, name, requestedRole);
        } catch (RuntimeException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("oauth_account_mapping_failed", ex.getMessage(), null), ex.getMessage(), ex);
        }
        return new SalonOAuth2User(
                user,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())),
                oauthUser.getAttributes(),
                resolveNameAttribute(userRequest));
    }

    private String resolveProviderUserId(OAuth2User user) {
        String sub = stringAttribute(user, "sub");
        if (sub != null) {
            return sub;
        }
        return stringAttribute(user, "id");
    }

    private String resolveNameAttribute(OAuth2UserRequest userRequest) {
        String attributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
        return attributeName == null || attributeName.isBlank() ? "email" : attributeName;
    }

    private String stringAttribute(OAuth2User user, String name) {
        Object value = user.getAttributes().get(name);
        return value == null ? null : String.valueOf(value);
    }
}
