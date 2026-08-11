package com.plateforme.credits.repository;

import com.plateforme.credits.entity.UserCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCreditRepository extends JpaRepository<UserCredit, UUID> {

    Optional<UserCredit> findByUser_Id(UUID userId);
}
