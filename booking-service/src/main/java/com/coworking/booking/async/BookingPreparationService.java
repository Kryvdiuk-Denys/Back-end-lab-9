package com.coworking.booking.async;

import com.coworking.booking.port.WorkspaceLookupPort;
import com.coworking.common.dto.WorkspaceDto;
import com.coworking.booking.dto.BookingPreparationRequest;
import com.coworking.booking.dto.BookingPreparationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class BookingPreparationService {

    private final WorkspaceLookupPort workspaceLookup;
    private final BookingAsyncLookupService lookupService;
    private final long waitTimeoutMillis;

    public BookingPreparationService(
            WorkspaceLookupPort workspaceLookup,
            BookingAsyncLookupService lookupService,
            @Value("${booking.async.preparation-timeout-ms:2500}") long waitTimeoutMillis
    ) {
        this.workspaceLookup = workspaceLookup;
        this.lookupService = lookupService;
        this.waitTimeoutMillis = waitTimeoutMillis;
    }

    @Transactional(readOnly = true)
    public CompletableFuture<BookingPreparationResult> prepareAsync(BookingPreparationRequest request) {
        validate(request);
        WorkspaceDto workspace = workspaceLookup.findById(request.workspaceId());
        long durationMinutes = Duration.between(request.startsAt(), request.endsAt()).toMinutes();

        CompletableFuture<Boolean> availability = lookupService.checkAvailability(request.workspaceId());
        CompletableFuture<java.math.BigDecimal> price = lookupService.requestPriceEstimate(
                workspace.getHourlyRate(), durationMinutes
        );

        return availability.thenCombine(price, BookingPreparationResult::ready)
                .exceptionally(exception -> {
                    log.error("Asynchronous booking preparation failed for workspace {}", request.workspaceId(),
                            exception);
                    return BookingPreparationResult.failed();
                });
    }

    public BookingPreparationResult prepare(BookingPreparationRequest request) {
        CompletableFuture<BookingPreparationResult> result;
        try {
            result = prepareAsync(request);
        } catch (TaskRejectedException exception) {
            throw new AsyncProcessingUnavailableException(
                    "Booking checks are busy. Please try again later.", exception
            );
        }

        try {
            return result.get(waitTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            result.cancel(true);
            throw new AsyncProcessingUnavailableException(
                    "Booking checks timed out. Please try again later.", exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AsyncProcessingUnavailableException(
                    "Booking checks were interrupted. Please try again later.", exception
            );
        } catch (ExecutionException exception) {
            log.error("Could not complete booking preparation", exception.getCause());
            throw new AsyncProcessingUnavailableException(
                    "Booking checks could not be completed. Please try again later.", exception.getCause()
            );
        }
    }

    private void validate(BookingPreparationRequest request) {
        if (request == null || request.workspaceId() == null) {
            throw new IllegalArgumentException("Workspace ID must not be null");
        }
        if (request.startsAt() == null || request.endsAt() == null) {
            throw new IllegalArgumentException("Booking start and end times must not be null");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new IllegalArgumentException("Booking end time must be after start time");
        }
    }
}
