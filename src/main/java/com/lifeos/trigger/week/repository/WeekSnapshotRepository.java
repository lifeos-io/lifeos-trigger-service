package com.lifeos.trigger.week.repository;

import com.lifeos.trigger.week.entity.WeekSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeekSnapshotRepository extends JpaRepository<WeekSnapshot, UUID> {

    @Modifying
    @Query("DELETE FROM WeekSnapshot w WHERE w.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
