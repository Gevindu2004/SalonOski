package com.saloonx.saloonx.security;

import com.saloonx.saloonx.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

public class SalonOAuth2User implements OAuth2User, SalonAuthenticatedUser {

    private final User user;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;
    private final String nameAttributeKey;

    public SalonOAuth2User(User user,
                           Collection<? extends GrantedAuthority> authorities,
                           Map<String, Object> attributes,
                           String nameAttributeKey) {
        this.user = user;
        this.authorities = authorities;
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        Object value = attributes.get(nameAttributeKey);
        return value == null ? user.getEmail() : String.valueOf(value);
    }

    @Override
    public User getAppUser() {
        return user;
    }
}
