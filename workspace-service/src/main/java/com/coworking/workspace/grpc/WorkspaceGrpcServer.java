package com.coworking.workspace.grpc;

import com.coworking.common.dto.WorkspaceDto;
import com.coworking.common.grpc.workspace.v1.GetWorkspaceRequest;
import com.coworking.common.grpc.workspace.v1.WorkspaceLookupServiceGrpc;
import com.coworking.common.grpc.workspace.v1.WorkspaceResponse;
import com.coworking.workspace.ResourceNotFoundException;
import com.coworking.workspace.WorkspaceService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "workspace.grpc.server", name = "enabled", havingValue = "true")
public class WorkspaceGrpcServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceGrpcServer.class);

    private final WorkspaceService workspaceService;
    private final String serviceToken;
    private final int port;
    private volatile Server server;

    public WorkspaceGrpcServer(
            WorkspaceService workspaceService,
            @Value("${workspace.grpc.server.port:9090}") int port,
            @Value("${security.internal-service-token}") String serviceToken
    ) {
        this.workspaceService = workspaceService;
        this.port = port;
        this.serviceToken = serviceToken;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        try {
            server = ServerBuilder.forPort(port)
                    .addService(ServerInterceptors.intercept(
                            new WorkspaceGrpcEndpoint(workspaceService),
                            new ServiceTokenServerInterceptor(serviceToken)
                    ))
                    .build()
                    .start();
            log.info("Workspace gRPC server started on port {}", port);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start workspace gRPC server on port " + port, exception);
        }
    }

    @Override
    public synchronized void stop() {
        Server currentServer = server;
        server = null;
        if (currentServer == null) {
            return;
        }

        currentServer.shutdown();
        try {
            if (!currentServer.awaitTermination(5, TimeUnit.SECONDS)) {
                currentServer.shutdownNow();
                currentServer.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            currentServer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Workspace gRPC server stopped");
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        Server currentServer = server;
        return currentServer != null && !currentServer.isShutdown();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private static final class WorkspaceGrpcEndpoint extends WorkspaceLookupServiceGrpc.WorkspaceLookupServiceImplBase {

        private final WorkspaceService workspaceService;

        private WorkspaceGrpcEndpoint(WorkspaceService workspaceService) {
            this.workspaceService = workspaceService;
        }

        @Override
        public void getWorkspace(
                GetWorkspaceRequest request,
                StreamObserver<WorkspaceResponse> responseObserver
        ) {
            try {
                WorkspaceDto workspace = workspaceService.findById(request.getId());
                WorkspaceResponse response = WorkspaceResponse.newBuilder()
                        .setId(workspace.getId())
                        .setCode(valueOrEmpty(workspace.getCode()))
                        .setName(valueOrEmpty(workspace.getName()))
                        .setType(valueOrEmpty(workspace.getType()))
                        .setCapacity(workspace.getCapacity())
                        .setHourlyRate(workspace.getHourlyRate().toPlainString())
                        .setActive(workspace.isActive())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (ResourceNotFoundException exception) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription(exception.getMessage())
                        .asRuntimeException());
            } catch (RuntimeException exception) {
                responseObserver.onError(Status.INTERNAL
                        .withDescription("Workspace lookup failed")
                        .withCause(exception)
                        .asRuntimeException());
            }
        }

        private static String valueOrEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
