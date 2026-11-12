package com.smartparking.payment.domain.repository;

import com.smartparking.payment.domain.model.DisputeEvidenceFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeEvidenceFileRepository extends JpaRepository<DisputeEvidenceFile, Long> {

    Optional<DisputeEvidenceFile> findByStoredFileName(String storedFileName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM DisputeEvidenceFile f WHERE f.storedFileName IN :storedFileNames")
    List<DisputeEvidenceFile> findByStoredFileNameInForUpdate(@Param("storedFileNames") Collection<String> storedFileNames);
}

