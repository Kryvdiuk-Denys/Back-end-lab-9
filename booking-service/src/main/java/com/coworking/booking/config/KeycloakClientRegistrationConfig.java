package com.coworking.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.Map;

@Configuration
public class KeycloakClientRegistrationConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${KEYCLOAK_ISSUER_URI:http://localhost:8081/realms/coworking}") String issuerUri,
            @Value("${KEYCLOAK_AUTHORIZATION_URI:http://localhost:8081/realms/coworking/protocol/openid-connect/auth}") String authorizationUri,
            @Value("${KEYCLOAK_TOKEN_URI:http://localhost:8081/realms/coworking/protocol/openid-connect/token}") String tokenUri,
            @Value("${KEYCLOAK_USER_INFO_URI:http://localhost:8081/realms/coworking/protocol/openid-connect/userinfo}") String userInfoUri,
            @Value("${KEYCLOAK_JWK_SET_URI:http://localhost:8081/realms/coworking/protocol/openid-connect/certs}") String jwkSetUri,
            @Value("${KEYCLOAK_LOGOUT_URI:http://localhost:8081/realms/coworking/protocol/openid-connect/logout}") String logoutUri,
            @Value("${KEYCLOAK_UI_CLIENT_ID:coworking-ui}") String clientId,
            @Value("${KEYCLOAK_UI_CLIENT_SECRET:ui-secret}") String clientSecret
    ) {
        ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .userNameAttributeName("preferred_username")
                .jwkSetUri(jwkSetUri)
                .issuerUri(issuerUri)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", logoutUri))
                .clientName("Keycloak")
                .build();

        return new InMemoryClientRegistrationRepository(keycloak);
    }
}
