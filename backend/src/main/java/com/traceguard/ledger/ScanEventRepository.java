package com.traceguard.ledger;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/** Append-only by construction: only save and read methods exist, so there is no way to update or delete. */
public interface ScanEventRepository extends Repository<ScanEvent, Long> {

    ScanEvent save(ScanEvent event);

    List<ScanEvent> findByTagIdOrderBySeqAsc(String tagId);

    Optional<ScanEvent> findFirstByTagIdOrderBySeqDesc(String tagId);

    boolean existsByTagIdAndNonce(String tagId, String nonce);
}
