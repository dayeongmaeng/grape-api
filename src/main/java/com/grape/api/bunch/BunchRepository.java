package com.grape.api.bunch;

import com.grape.api.bunch.entity.Bunch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BunchRepository extends JpaRepository<Bunch, UUID> {

    List<Bunch> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Bunch> findByIdAndUserId(UUID id, UUID userId);

    /** Guest-merge case B: move every bunch owned by {@code source} to {@code target}. */
    @Modifying(flushAutomatically = true)
    @Query("update Bunch b set b.userId = :target where b.userId = :source")
    int reassignOwner(@Param("source") UUID source, @Param("target") UUID target);
}
