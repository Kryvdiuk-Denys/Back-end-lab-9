package com.coworking.booking.client.http;

import com.coworking.booking.port.WorkspaceManagementPort;
import com.coworking.common.dto.WorkspaceDto;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceHttpManagementAdapter implements WorkspaceManagementPort {

    private final WorkspaceHttpRequestExecutor requests;

    public WorkspaceHttpManagementAdapter(WorkspaceHttpRequestExecutor requests) {
        this.requests = requests;
    }

    @Override
    public WorkspaceDto create(WorkspaceDto workspace) {
        return requests.create(workspace);
    }

    @Override
    public WorkspaceDto update(Long id, WorkspaceDto workspace) {
        return requests.update(id, workspace);
    }

    @Override
    public void delete(Long id) {
        requests.delete(id);
    }
}
