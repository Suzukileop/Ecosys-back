package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentReport;
import com.plateforme.marketplace.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReport, UUID> {

    Page<ContentReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);
}
