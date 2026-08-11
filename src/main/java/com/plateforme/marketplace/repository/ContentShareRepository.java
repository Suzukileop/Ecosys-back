package com.plateforme.marketplace.repository;

import com.plateforme.marketplace.entity.ContentShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContentShareRepository extends JpaRepository<ContentShare, UUID> {
}
