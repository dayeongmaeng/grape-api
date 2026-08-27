package com.grape.api.bunch;

import com.grape.api.bunch.entity.BunchFillEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BunchFillEventRepository extends JpaRepository<BunchFillEvent, Long> {

    /** fillDates for one bunch, oldest first. Duplicates are kept — never de-duplicate (§3-3). */
    @Query("select e.fillDate from BunchFillEvent e where e.bunchId = :bunchId order by e.createdAt asc, e.id asc")
    List<LocalDate> findFillDates(@Param("bunchId") UUID bunchId);

    /** Batched variant for list endpoints. Group by bunchId in memory (relative order preserved). */
    @Query("select e.bunchId as bunchId, e.fillDate as fillDate from BunchFillEvent e "
            + "where e.bunchId in :bunchIds order by e.createdAt asc, e.id asc")
    List<FillDateRow> findFillDatesFor(@Param("bunchIds") Collection<UUID> bunchIds);

    interface FillDateRow {
        UUID getBunchId();

        LocalDate getFillDate();
    }
}
