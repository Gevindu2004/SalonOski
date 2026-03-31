package com.saloonx.saloonx.security;

import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SalonOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final UserService userService;
    private final HttpSession session;

    public SalonOidcUserService(UserService userService, HttpSession session) {
        this.userService = userService;
        this.session = session;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        String requestedRole = String.valueOf(session.getAttribute("oauth_user_role"));
        User user;
        try {
            user = userService.provisionOAuthUser(
                    provider,
                    oidcUser.getSubject(),
                    oidcUser.getEmail(),
                    oidcUser.getFullName(),
                    requestedRole);
        } catch (RuntimeException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("oauth_account_mapping_failed", ex.getMessage(), null), ex.getMessage(), ex);
        }
        return new SalonOidcUser(user, oidcUser, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }
}
