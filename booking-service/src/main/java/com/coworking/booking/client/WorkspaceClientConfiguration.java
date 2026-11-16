package com.coworking.booking.client;

import com.coworking.booking.client.grpc.GrpcWorkspaceLookupAdapter;
import com.coworking.booking.client.http.WorkspaceHttpLookupAdapter;
import com.coworking.booking.client.http.WorkspaceHttpRequestExecutor;
import com.coworking.booking.port.WorkspaceLookupPort;
import com.coworking.common.grpc.InterserviceMetadata;
import com.coworking.common.grpc.workspace.v1.WorkspaceLookupServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Locale;

@Configuration
public class WorkspaceClientConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "services.workspace", name = "client-type", havingValue = "grpc")
    ManagedChannel workspaceGrpcChannel(
            @Value("${services.workspace.grpc.target:localhost:9090}") String target
    ) {
        return ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
    }

    @Bean
    WorkspaceLookupPort workspaceLookupPort(
            WorkspaceHttpRequestExecutor httpRequests,
            ObjectProvider<ManagedChannel> grpcChannel,
            @Value("${services.workspace.client-type:http}") String clientType,
            @Value("${services.workspace.grpc.deadline:2s}") Duration deadline,
            @Value("${services.workspace.service-token:local-workspace-token}") String serviceToken
    ) {
        return switch (clientType.trim().toLowerCase(Locale.ROOT)) {
            case "http" -> new WorkspaceHttpLookupAdapter(httpRequests);
            case "grpc" -> {
                Metadata headers = new Metadata();
                headers.put(
                        Metadata.Key.of(InterserviceMetadata.SERVICE_TOKEN_HEADER, Metadata.ASCII_STRING_MARSHALLER),
                        serviceToken
                );
                WorkspaceLookupServiceGrpc.WorkspaceLookupServiceBlockingStub stub =
                        WorkspaceLookupServiceGrpc.newBlockingStub(grpcChannel.getObject())
                                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
                yield new GrpcWorkspaceLookupAdapter(stub, deadline);
            }
            default -> throw new IllegalStateException(
                    "Unknown services.workspace.client-type '" + clientType + "'. Expected 'http' or 'grpc'."
            );
        };
    }
}
