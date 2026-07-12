package com.nexustrade.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WalletSnapshotRepository extends JpaRepository<WalletSnapshotEntity, Long> {
    List<WalletSnapshotEntity> findTop10ByExchangeOrderByTsDesc(String exchange);
}
