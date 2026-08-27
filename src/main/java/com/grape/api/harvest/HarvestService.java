package com.grape.api.harvest;

import com.grape.api.bunch.BunchRepository;
import com.grape.api.bunch.dto.BunchResponse;
import com.grape.api.bunch.entity.Bunch;
import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.harvest.dto.HarvestResponse;
import com.grape.api.harvest.dto.RecallRequest;
import com.grape.api.harvest.entity.Harvest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HarvestService {

    private final HarvestRepository harvestRepository;
    private final BunchRepository bunchRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<HarvestResponse> list(UUID userId) {
        return harvestRepository.findByUserIdOrderByHarvestedAtDesc(userId).stream()
                .map(HarvestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public HarvestResponse get(UUID userId, UUID harvestId) {
        return HarvestResponse.from(requireHarvest(userId, harvestId));
    }

    @Transactional
    public void delete(UUID userId, UUID harvestId) {
        harvestRepository.delete(requireHarvest(userId, harvestId));
    }

    /**
     * §3-4: delete the harvest and insert a brand-new active bunch. The new bunch gets a fresh UUID
     * (the original sourceBunchId is never reused).
     */
    @Transactional
    public BunchResponse recall(UUID userId, UUID harvestId, RecallRequest request) {
        Harvest harvest = requireHarvest(userId, harvestId);
        int filled = Math.max(0, Math.min(harvest.getCount(), request.filled()));
        harvestRepository.delete(harvest);
        Bunch bunch = bunchRepository.save(
                Bunch.fromRecall(userId, harvest.getName(), harvest.getCount(), filled, clock.instant()));
        return BunchResponse.of(bunch, List.of());
    }

    private Harvest requireHarvest(UUID userId, UUID harvestId) {
        return harvestRepository.findByIdAndUserId(harvestId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Harvest not found"));
    }
}
