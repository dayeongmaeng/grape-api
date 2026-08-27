package com.grape.api.bunch;

import com.grape.api.bunch.dto.ArchiveResponse;
import com.grape.api.bunch.dto.BunchResponse;
import com.grape.api.bunch.dto.CreateBunchRequest;
import com.grape.api.bunch.dto.FillRequest;
import com.grape.api.bunch.dto.ReplantResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bunches")
@RequiredArgsConstructor
public class BunchController {

    private final BunchService bunchService;

    @GetMapping
    public List<BunchResponse> list(@AuthenticationPrincipal UUID userId) {
        return bunchService.list(userId);
    }

    @GetMapping("/{id}")
    public BunchResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return bunchService.get(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BunchResponse create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateBunchRequest request) {
        return bunchService.create(userId, request);
    }

    @PatchMapping("/{id}/fill")
    public BunchResponse fill(@AuthenticationPrincipal UUID userId,
                              @PathVariable UUID id,
                              @Valid @RequestBody FillRequest request) {
        return bunchService.fill(userId, id, request);
    }

    @PostMapping("/{id}/replant")
    public ReplantResponse replant(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return bunchService.replant(userId, id);
    }

    @PostMapping("/{id}/archive")
    public ArchiveResponse archive(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return bunchService.archive(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        bunchService.delete(userId, id);
    }
}
