package com.saloonx.saloonx.service;

import com.saloonx.saloonx.config.DynamicClientRegistrationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConfiguredOAuthProviderService {

    private final DynamicClientRegistrationRepository clientRegistrationRepository;

    public ConfiguredOAuthProviderService(DynamicClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    public List<String> getAvailableProviders() {
        List<String> providers = new ArrayList<>();
        for (var registration : clientRegistrationRepository) {
            providers.add(registration.getRegistrationId().toLowerCase());
        }
        return providers;
    }

    public boolean isProviderAvailable(String provider) {
        return getAvailableProviders().contains(provider.toLowerCase());
    }
}
