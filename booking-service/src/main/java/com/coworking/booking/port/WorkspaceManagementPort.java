package com.coworking.booking.port;

import com.coworking.common.dto.WorkspaceDto;

public interface WorkspaceManagementPort {

    WorkspaceDto create(WorkspaceDto workspace);

    WorkspaceDto update(Long id, WorkspaceDto workspace);

    void delete(Long id);
}
