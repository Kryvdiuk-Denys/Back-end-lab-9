package com.coworking.booking.client.http;

import com.coworking.booking.WorkspaceNotFoundException;
import com.coworking.booking.WorkspaceServiceUnavailableException;
import com.coworking.common.dto.WorkspaceDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.function.Supplier;

@Component
public class WorkspaceHttpRequestExecutor {

    private final WorkspaceHttpApi api;

    public WorkspaceHttpRequestExecutor(WorkspaceHttpApi api) {
        this.api = api;
    }

    public List<WorkspaceDto> findAll() {
        return execute("workspaces", api::findAll);
    }

    public WorkspaceDto findById(Long id) {
        return execute("Workspace " + id, () -> api.findById(id));
    }

    public WorkspaceDto create(WorkspaceDto workspace) {
        return execute("workspace", () -> api.create(workspace));
    }

    public WorkspaceDto update(Long id, WorkspaceDto workspace) {
        return execute("Workspace " + id, () -> api.update(id, workspace));
    }

    public void delete(Long id) {
        execute("Workspace " + id, () -> {
            api.delete(id);
            return null;
        });
    }

    private <T> T execute(String resource, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new WorkspaceNotFoundException(resource + " not found", exception);
            }
            throw new WorkspaceServiceUnavailableException("Workspace service is unavailable", exception);
        } catch (RestClientException exception) {
            throw new WorkspaceServiceUnavailableException("Workspace service is unavailable", exception);
        }
    }
}
