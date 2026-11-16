package com.coworking.booking.client.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class WorkspaceHttpConfiguration {

    @Bean
    RestClient workspaceRestClient(
            RestClient.Builder builder,
            @Value("${services.workspace.base-url:http://localhost:8082}") String baseUrl,
            @Value("${services.workspace.service-token:local-workspace-token}") String serviceToken,
            @Value("${services.workspace.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${services.workspace.http.read-timeout:3s}") Duration readTimeout
    ) {
        requirePositive(connectTimeout, "services.workspace.http.connect-timeout");
        requirePositive(readTimeout, "services.workspace.http.read-timeout");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Token", serviceToken)
                .defaultHeader("X-Tenant-ID", "booking-service")
                .build();
    }

    @Bean
    WorkspaceHttpApi workspaceHttpApi(RestClient workspaceRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(workspaceRestClient))
                .build();
        return factory.createClient(WorkspaceHttpApi.class);
    }

    private static void requirePositive(Duration duration, String property) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(property + " must be greater than zero");
        }
    }
}
