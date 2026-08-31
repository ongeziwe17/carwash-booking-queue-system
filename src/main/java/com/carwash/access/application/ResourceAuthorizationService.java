package com.carwash.access.application;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("resourceAuthorization")
public class ResourceAuthorizationService {

    public boolean isSelf(Authentication authentication, String id) {
        return elevatedAdmin(authentication) || authentication != null && authentication.getName().equals(id);
    }

    private boolean elevatedAdmin(Authentication authentication) {
        return has(authentication, "ROLE_PLATFORM_ADMIN");
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }
}
