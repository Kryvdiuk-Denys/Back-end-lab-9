package com.coworking.booking.port;

import com.coworking.common.dto.WorkspaceDto;

public interface WorkspaceLookupPort {

    WorkspaceDto findById(Long id);
}
