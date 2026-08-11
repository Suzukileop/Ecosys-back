package com.plateforme.credits.repository;

import com.plateforme.credits.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {
}
