package com.saloonx.saloonx.security;

import com.saloonx.saloonx.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SalonOidcUser implements OidcUser, SalonAuthenticatedUser {

    private final User user;
    private final OidcUser delegate;
    private final Collection<? extends GrantedAuthority> authorities;

    public SalonOidcUser(User user, OidcUser delegate, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.delegate = delegate;
        this.authorities = authorities;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public List<String> getAudience() {
        return delegate.getAudience();
    }

    @Override
    public String getEmail() {
        return delegate.getEmail();
    }

    @Override
    public String getFullName() {
        return delegate.getFullName();
    }

    @Override
    public String getGivenName() {
        return delegate.getGivenName();
    }

    @Override
    public String getFamilyName() {
        return delegate.getFamilyName();
    }

    @Override
    public User getAppUser() {
        return user;
    }
}
