package com.saloonx.saloonx.security;

import com.saloonx.saloonx.config.OAuth2ProviderProperties;
import com.saloonx.saloonx.model.Beautician;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.service.BeauticianService;
import com.saloonx.saloonx.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class SalonAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final BeauticianService beauticianService;
    private final OAuth2ProviderProperties properties;

    public SalonAuthenticationSuccessHandler(UserService userService,
                                             BeauticianService beauticianService,
                                             OAuth2ProviderProperties properties) {
        this.userService = userService;
        this.beauticianService = beauticianService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof SalonAuthenticatedUser authenticatedUser)) {
            response.sendRedirect("/login?error");
            return;
        }

        User sessionUser = userService.recordSuccessfulLogin(authenticatedUser.getAppUser());
        HttpSession session = request.getSession(true);
        session.setAttribute("user", sessionUser);
        session.removeAttribute("oauth_user_role");
        session.removeAttribute("oauth_entry_flow");

        response.sendRedirect(resolveTarget(sessionUser));
    }

    private String resolveTarget(User user) {
        if ("ADMIN".equals(user.getRole())) {
            return "/admin/dashboard";
        }
        if ("BEAUTICIAN".equals(user.getRole())) {
            Optional<Beautician> beauticianOpt = beauticianService.getBeauticianByUserId(user.getId());
            if (beauticianOpt.isEmpty()) {
                return "/beautician/profile/create?setupRequired";
            }

            Beautician beautician = beauticianOpt.get();
            if (!beauticianService.isApproved(beautician)) {
                return "/beautician/profile?approvalPending";
            }
            return "/beautician/dashboard";
        }
        return properties.getDefaultSuccessUrl();
    }
}
