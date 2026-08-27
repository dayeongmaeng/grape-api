package com.grape.api.harvest;

import com.grape.api.bunch.dto.BunchResponse;
import com.grape.api.harvest.dto.HarvestResponse;
import com.grape.api.harvest.dto.RecallRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/harvests")
@RequiredArgsConstructor
public class HarvestController {

    private final HarvestService harvestService;

    @GetMapping
    public List<HarvestResponse> list(@AuthenticationPrincipal UUID userId) {
        return harvestService.list(userId);
    }

    @GetMapping("/{id}")
    public HarvestResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return harvestService.get(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        harvestService.delete(userId, id);
    }

    @PostMapping("/{id}/recall")
    public BunchResponse recall(@AuthenticationPrincipal UUID userId,
                                @PathVariable UUID id,
                                @Valid @RequestBody RecallRequest request) {
        return harvestService.recall(userId, id, request);
    }
}
