package vn.omnismart.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.omnismart.auth.OmniSmartOidcUserService;
import vn.omnismart.auth.DiscardingOAuth2AuthorizedClientRepository;
import vn.omnismart.auth.OAuthRateLimitFilter;
import vn.omnismart.auth.OAuthRateLimiter;
import vn.omnismart.common.api.ApiSecurityErrorWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OmniSmartOidcUserService oidcUserService,
            OAuthRateLimiter oauthRateLimiter,
            ApiSecurityErrorWriter apiSecurityErrorWriter,
            @Value("${omnismart.frontend-url}") String frontendUrl) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        PathPatternRequestMatcher apiMatcher = PathPatternRequestMatcher.pathPattern("/api/**");

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/system/status",
                                "/api/v1/auth/csrf",
                                "/oauth2/**",
                                "/login/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                apiSecurityErrorWriter.authenticationEntryPoint(), apiMatcher)
                        .defaultAccessDeniedHandlerFor(
                                apiSecurityErrorWriter.accessDeniedHandler(), apiMatcher))
                .oauth2Login(oauth2 -> oauth2
                        .authorizedClientRepository(new DiscardingOAuth2AuthorizedClientRepository())
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(
                                        new HttpSessionOAuth2AuthorizationRequestRepository()))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .successHandler(successHandler(frontendUrl))
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler(
                                frontendUrl + "/login?error=oauth")))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"));

        http.addFilterBefore(
                new OAuthRateLimitFilter(oauthRateLimiter),
                OAuth2AuthorizationRequestRedirectFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${omnismart.frontend-url}") String frontendUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private SimpleUrlAuthenticationSuccessHandler successHandler(String frontendUrl) {
        SimpleUrlAuthenticationSuccessHandler handler =
                new SimpleUrlAuthenticationSuccessHandler(frontendUrl + "/app");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }
}
