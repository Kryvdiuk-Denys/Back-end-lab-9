package com.coworking.booking.client.grpc;

import com.coworking.booking.WorkspaceNotFoundException;
import com.coworking.booking.WorkspaceServiceUnavailableException;
import com.coworking.booking.port.WorkspaceLookupPort;
import com.coworking.common.dto.WorkspaceDto;
import com.coworking.common.grpc.workspace.v1.GetWorkspaceRequest;
import com.coworking.common.grpc.workspace.v1.WorkspaceLookupServiceGrpc;
import com.coworking.common.grpc.workspace.v1.WorkspaceResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class GrpcWorkspaceLookupAdapter implements WorkspaceLookupPort {

    private final WorkspaceLookupServiceGrpc.WorkspaceLookupServiceBlockingStub stub;
    private final Duration deadline;

    public GrpcWorkspaceLookupAdapter(
            WorkspaceLookupServiceGrpc.WorkspaceLookupServiceBlockingStub stub,
            Duration deadline
    ) {
        if (deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException("services.workspace.grpc.deadline must be greater than zero");
        }
        this.stub = stub;
        this.deadline = deadline;
    }

    @Override
    public WorkspaceDto findById(Long id) {
        try {
            WorkspaceResponse response = stub
                    .withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS)
                    .getWorkspace(GetWorkspaceRequest.newBuilder().setId(id).build());
            return toDto(response);
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new WorkspaceNotFoundException("Workspace " + id + " not found", exception);
            }
            throw new WorkspaceServiceUnavailableException("Workspace service is unavailable", exception);
        } catch (RuntimeException exception) {
            throw new WorkspaceServiceUnavailableException("Workspace service is unavailable", exception);
        }
    }

    private WorkspaceDto toDto(WorkspaceResponse response) {
        WorkspaceDto workspace = new WorkspaceDto();
        workspace.setId(response.getId());
        workspace.setCode(response.getCode());
        workspace.setName(response.getName());
        workspace.setType(response.getType());
        workspace.setCapacity(response.getCapacity());
        workspace.setHourlyRate(new BigDecimal(response.getHourlyRate()));
        workspace.setActive(response.getActive());
        return workspace;
    }
}
