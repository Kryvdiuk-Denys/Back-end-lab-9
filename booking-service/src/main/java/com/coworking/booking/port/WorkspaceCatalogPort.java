package com.coworking.booking.port;

import com.coworking.common.dto.WorkspaceDto;

import java.util.List;

public interface WorkspaceCatalogPort {

    List<WorkspaceDto> findAll();
}
