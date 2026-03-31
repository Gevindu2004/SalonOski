package com.saloonx.saloonx.config;

import com.saloonx.saloonx.security.CustomUserDetailsService;
import com.saloonx.saloonx.security.OAuth2FlowTrackingAuthorizationRequestResolver;
import com.saloonx.saloonx.security.SalonAuthenticationFailureHandler;
import com.saloonx.saloonx.security.SalonAuthenticationSuccessHandler;
import com.saloonx.saloonx.security.SalonOAuth2UserService;
import com.saloonx.saloonx.security.SalonOidcUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DynamicClientRegistrationRepository clientRegistrationRepository,
                                                   HttpSession session,
                                                   CustomUserDetailsService userDetailsService,
                                                   SalonAuthenticationSuccessHandler successHandler,
                                                   SalonAuthenticationFailureHandler failureHandler,
                                                   SalonOAuth2UserService oauth2UserService,
                                                   SalonOidcUserService oidcUserService,
                                                   AuthenticationProvider authenticationProvider) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/home",
                                "/about",
                                "/services",
                                "/ai-hairstyle",
                                "/login",
                                "/signup",
                                "/beautician-signup",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**")
                        .permitAll()
                        .requestMatchers("/accounting", "/admin/accounting", "/api/accounting/**", "/admin/inventory/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/beautician/**", "/beautician-bookings").hasAnyRole("BEAUTICIAN", "ADMIN")
                        .requestMatchers("/appointment/**", "/profile/**", "/my-appointments/**", "/notifications/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(request -> "GET".equals(request.getMethod()) && "/logout".equals(request.getServletPath()))
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"));

        if (clientRegistrationRepository.iterator().hasNext()) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .authorizationEndpoint(endpoint -> endpoint
                            .baseUri(OAuth2FlowTrackingAuthorizationRequestResolver.AUTHORIZATION_BASE_URI)
                            .authorizationRequestResolver(
                                    new OAuth2FlowTrackingAuthorizationRequestResolver(clientRegistrationRepository, session)))
                    .userInfoEndpoint(endpoint -> endpoint
                            .userService(oauth2UserService)
                            .oidcUserService(oidcUserService))
                    .successHandler(successHandler)
                    .failureHandler(failureHandler));
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
