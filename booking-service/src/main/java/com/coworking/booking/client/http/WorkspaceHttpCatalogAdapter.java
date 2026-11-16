package com.coworking.booking.client.http;

import com.coworking.booking.port.WorkspaceCatalogPort;
import com.coworking.common.dto.WorkspaceDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkspaceHttpCatalogAdapter implements WorkspaceCatalogPort {

    private final WorkspaceHttpRequestExecutor requests;

    public WorkspaceHttpCatalogAdapter(WorkspaceHttpRequestExecutor requests) {
        this.requests = requests;
    }

    @Override
    public List<WorkspaceDto> findAll() {
        return requests.findAll();
    }
}
