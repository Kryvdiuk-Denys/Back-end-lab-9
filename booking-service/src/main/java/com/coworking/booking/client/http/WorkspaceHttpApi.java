package com.coworking.booking.client.http;

import com.coworking.common.dto.WorkspaceDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/** Declarative HTTP contract for the workspace service. */
public interface WorkspaceHttpApi {

    @GetExchange("/api/workspaces")
    List<WorkspaceDto> findAll();

    @GetExchange("/api/workspaces/{id}")
    WorkspaceDto findById(@PathVariable Long id);

    @PostExchange("/api/workspaces")
    WorkspaceDto create(@RequestBody WorkspaceDto workspace);

    @PutExchange("/api/workspaces/{id}")
    WorkspaceDto update(@PathVariable Long id, @RequestBody WorkspaceDto workspace);

    @DeleteExchange("/api/workspaces/{id}")
    void delete(@PathVariable Long id);
}
