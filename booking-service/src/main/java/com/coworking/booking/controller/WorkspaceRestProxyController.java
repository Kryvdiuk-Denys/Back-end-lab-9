package com.coworking.booking.controller;

import com.coworking.booking.WorkspaceUsageGuard;
import com.coworking.booking.port.WorkspaceCatalogPort;
import com.coworking.booking.port.WorkspaceLookupPort;
import com.coworking.booking.port.WorkspaceManagementPort;
import com.coworking.common.dto.WorkspaceDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceRestProxyController {

    private final WorkspaceLookupPort workspaceLookup;
    private final WorkspaceCatalogPort workspaceCatalog;
    private final WorkspaceManagementPort workspaceManagement;
    private final WorkspaceUsageGuard workspaceUsageGuard;

    public WorkspaceRestProxyController(
            WorkspaceLookupPort workspaceLookup,
            WorkspaceCatalogPort workspaceCatalog,
            WorkspaceManagementPort workspaceManagement,
            WorkspaceUsageGuard workspaceUsageGuard
    ) {
        this.workspaceLookup = workspaceLookup;
        this.workspaceCatalog = workspaceCatalog;
        this.workspaceManagement = workspaceManagement;
        this.workspaceUsageGuard = workspaceUsageGuard;
    }

    @GetMapping
    public List<WorkspaceDto> findAll() {
        return workspaceCatalog.findAll();
    }

    @GetMapping("/{id}")
    public WorkspaceDto findById(@PathVariable Long id) {
        return workspaceLookup.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceDto create(@Valid @RequestBody WorkspaceDto dto) {
        return workspaceManagement.create(dto);
    }

    @PutMapping("/{id}")
    public WorkspaceDto update(@PathVariable Long id, @Valid @RequestBody WorkspaceDto dto) {
        return workspaceManagement.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        workspaceUsageGuard.ensureWorkspaceCanBeDeleted(id);
        workspaceManagement.delete(id);
    }
}
