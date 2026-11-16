package com.coworking.booking.client.http;

import com.coworking.booking.port.WorkspaceLookupPort;
import com.coworking.common.dto.WorkspaceDto;

public class WorkspaceHttpLookupAdapter implements WorkspaceLookupPort {

    private final WorkspaceHttpRequestExecutor requests;

    public WorkspaceHttpLookupAdapter(WorkspaceHttpRequestExecutor requests) {
        this.requests = requests;
    }

    @Override
    public WorkspaceDto findById(Long id) {
        return requests.findById(id);
    }
}
