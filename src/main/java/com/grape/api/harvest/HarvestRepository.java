package com.grape.api.harvest;

import com.grape.api.harvest.entity.Harvest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HarvestRepository extends JpaRepository<Harvest, UUID> {

    List<Harvest> findByUserIdOrderByHarvestedAtDesc(UUID userId);

    Optional<Harvest> findByIdAndUserId(UUID id, UUID userId);

    /** Guest-merge case B: move every harvest owned by {@code source} to {@code target}. */
    @Modifying(flushAutomatically = true)
    @Query("update Harvest h set h.userId = :target where h.userId = :source")
    int reassignOwner(@Param("source") UUID source, @Param("target") UUID target);
}
