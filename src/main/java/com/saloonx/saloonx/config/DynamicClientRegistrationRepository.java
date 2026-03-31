package com.saloonx.saloonx.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DynamicClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private final Map<String, ClientRegistration> registrations;

    public DynamicClientRegistrationRepository(List<ClientRegistration> registrations) {
        this.registrations = new LinkedHashMap<>();
        for (ClientRegistration registration : registrations) {
            this.registrations.put(registration.getRegistrationId(), registration);
        }
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }
}
