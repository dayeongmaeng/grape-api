package com.grape.api.bunch;

import com.grape.api.bunch.dto.ArchiveResponse;
import com.grape.api.bunch.dto.BunchResponse;
import com.grape.api.bunch.dto.CreateBunchRequest;
import com.grape.api.bunch.dto.FillRequest;
import com.grape.api.bunch.dto.ReplantResponse;
import com.grape.api.bunch.entity.Bunch;
import com.grape.api.bunch.entity.BunchFillEvent;
import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.common.GrapeTime;
import com.grape.api.harvest.HarvestRepository;
import com.grape.api.harvest.dto.HarvestResponse;
import com.grape.api.harvest.entity.Harvest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BunchService {

    private final BunchRepository bunchRepository;
    private final BunchFillEventRepository fillEventRepository;
    private final HarvestRepository harvestRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<BunchResponse> list(UUID userId) {
        List<Bunch> bunches = bunchRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (bunches.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<LocalDate>> fillDates = loadFillDates(bunches.stream().map(Bunch::getId).toList());
        return bunches.stream()
                .map(bunch -> BunchResponse.of(bunch, fillDates.getOrDefault(bunch.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BunchResponse get(UUID userId, UUID bunchId) {
        Bunch bunch = requireBunch(userId, bunchId);
        return BunchResponse.of(bunch, fillEventRepository.findFillDates(bunchId));
    }

    @Transactional
    public BunchResponse create(UUID userId, CreateBunchRequest request) {
        Bunch bunch = bunchRepository.save(Bunch.create(
                userId, request.name(), request.unitLabel(), request.total(), request.periodDays(), clock.instant()));
        return BunchResponse.of(bunch, List.of());
    }

    @Transactional
    public BunchResponse fill(UUID userId, UUID bunchId, FillRequest request) {
        Instant now = clock.instant();
        Bunch bunch = requireBunch(userId, bunchId);
        boolean increased = bunch.applyFill(request.filled(), now);
        if (increased) {
            LocalDate today = LocalDate.ofInstant(now, GrapeTime.ZONE);
            fillEventRepository.save(BunchFillEvent.of(bunchId, today, now));
        }
        return BunchResponse.of(bunch, fillEventRepository.findFillDates(bunchId));
    }

    @Transactional
    public ReplantResponse replant(UUID userId, UUID bunchId) {
        Instant now = clock.instant();
        Bunch bunch = requireBunch(userId, bunchId);
        Harvest harvest = harvestRepository.save(
                Harvest.snapshot(userId, bunch.getId(), bunch.getName(), bunch.getTotal(), now));
        bunch.resetForReplant(now);
        // fill_events are intentionally kept (fillDates accumulate across cycles, §6)
        return new ReplantResponse(
                HarvestResponse.from(harvest),
                BunchResponse.of(bunch, fillEventRepository.findFillDates(bunchId)));
    }

    @Transactional
    public ArchiveResponse archive(UUID userId, UUID bunchId) {
        Instant now = clock.instant();
        Bunch bunch = requireBunch(userId, bunchId);
        Harvest harvest = harvestRepository.save(
                Harvest.snapshot(userId, bunch.getId(), bunch.getName(), bunch.getTotal(), now));
        bunchRepository.delete(bunch); // DB ON DELETE CASCADE removes bunch_fill_events
        return new ArchiveResponse(HarvestResponse.from(harvest));
    }

    @Transactional
    public void delete(UUID userId, UUID bunchId) {
        bunchRepository.delete(requireBunch(userId, bunchId));
    }

    private Bunch requireBunch(UUID userId, UUID bunchId) {
        return bunchRepository.findByIdAndUserId(bunchId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Bunch not found"));
    }

    private Map<UUID, List<LocalDate>> loadFillDates(List<UUID> bunchIds) {
        Map<UUID, List<LocalDate>> byBunch = new HashMap<>();
        for (BunchFillEventRepository.FillDateRow row : fillEventRepository.findFillDatesFor(bunchIds)) {
            byBunch.computeIfAbsent(row.getBunchId(), key -> new ArrayList<>()).add(row.getFillDate());
        }
        return byBunch;
    }
}
