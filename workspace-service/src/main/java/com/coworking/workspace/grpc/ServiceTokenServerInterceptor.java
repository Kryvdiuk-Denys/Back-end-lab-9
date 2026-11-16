package com.coworking.workspace.grpc;

import com.coworking.common.grpc.InterserviceMetadata;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class ServiceTokenServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> SERVICE_TOKEN_KEY = Metadata.Key.of(
            InterserviceMetadata.SERVICE_TOKEN_HEADER,
            Metadata.ASCII_STRING_MARSHALLER
    );

    private final String expectedToken;

    public ServiceTokenServerInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
            ServerCall<RequestT, ResponseT> call,
            Metadata headers,
            ServerCallHandler<RequestT, ResponseT> next
    ) {
        if (!expectedToken.equals(headers.get(SERVICE_TOKEN_KEY))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid service token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
