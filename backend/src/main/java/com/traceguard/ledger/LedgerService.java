package com.traceguard.ledger;

import com.traceguard.catalog.CatalogRepositories.TagRepository;
import com.traceguard.common.Errors.BadRequestException;
import com.traceguard.common.Errors.ConflictException;
import com.traceguard.common.Errors.NotFoundException;
import com.traceguard.crypto.HashUtil;
import com.traceguard.crypto.KeyService;
import com.traceguard.crypto.SignatureService;
import com.traceguard.org.Checkpoint;
import com.traceguard.org.OrgRepositories.CheckpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Creates signed, hash-chained events and checks a tag's chain.
 *
 * <pre>
 * hash      = SHA-256(tagId | checkpointId | eventType | occurredAt | seq | nonce | prevHash)
 * signature = ECDSA(checkpoint private key, hash)
 * </pre>
 */
@Service
public class LedgerService {

    public record ChainReport(String tagId, boolean valid, int eventCount, List<String> problems) {}

    private final ScanEventRepository events;
    private final TagRepository tags;
    private final CheckpointRepository checkpoints;
    private final KeyService keys;
    private final SignatureService signatures;

    public LedgerService(ScanEventRepository events, TagRepository tags, CheckpointRepository checkpoints,
                         KeyService keys, SignatureService signatures) {
        this.events = events;
        this.tags = tags;
        this.checkpoints = checkpoints;
        this.keys = keys;
        this.signatures = signatures;
    }

    /** Appends one event to the tag's chain. {@code occurredAt} and {@code nonce} default to now / random. */
    @Transactional
    public ScanEvent append(String tagId, long checkpointId, ScanEvent.Type type, Instant occurredAt, String nonce) {
        if (!tags.existsById(tagId)) {
            throw new NotFoundException("Tag " + tagId + " is not registered");
        }
        Checkpoint cp = checkpoints.findById(checkpointId)
                .orElseThrow(() -> new NotFoundException("Checkpoint " + checkpointId + " not found"));
        if (!cp.isActive()) {
            throw new BadRequestException("Checkpoint " + checkpointId + " is not active");
        }
        String n = (nonce == null || nonce.isBlank()) ? UUID.randomUUID().toString() : nonce;
        if (events.existsByTagIdAndNonce(tagId, n)) {
            throw new ConflictException("Replayed scan: nonce already used for this tag");
        }
        // Millisecond precision so the stored value round-trips exactly through the database.
        Instant at = (occurredAt == null ? Instant.now() : occurredAt).truncatedTo(ChronoUnit.MILLIS);

        Optional<ScanEvent> last = events.findFirstByTagIdOrderBySeqDesc(tagId);
        int seq = last.map(e -> e.getSeq() + 1).orElse(1);
        String prevHash = last.map(ScanEvent::getHash).orElse(HashUtil.GENESIS_HASH);

        String hash = computeHash(tagId, checkpointId, type, at, seq, n, prevHash);
        String signature = signatures.sign(keys.privateKey(checkpointId), hash);
        return events.save(new ScanEvent(tagId, checkpointId, type, at, seq, n, prevHash, hash, signature));
    }

    @Transactional(readOnly = true)
    public List<ScanEvent> eventsFor(String tagId) {
        return events.findByTagIdOrderBySeqAsc(tagId);
    }

    /** Re-computes every hash, link and signature of the tag's chain. */
    @Transactional(readOnly = true)
    public ChainReport checkChain(String tagId) {
        List<ScanEvent> chain = events.findByTagIdOrderBySeqAsc(tagId);
        List<String> problems = new ArrayList<>();
        Map<Long, Checkpoint> cache = new HashMap<>();
        String expectedPrev = HashUtil.GENESIS_HASH;
        int expectedSeq = 1;

        for (ScanEvent e : chain) {
            if (e.getSeq() != expectedSeq) {
                problems.add("Event " + e.getSeq() + ": expected sequence " + expectedSeq);
            }
            if (!e.getPrevHash().equals(expectedPrev)) {
                problems.add("Event " + e.getSeq() + ": previous-hash link is broken");
            }
            String recomputed = computeHash(e.getTagId(), e.getCheckpointId(), e.getEventType(),
                    e.getOccurredAt(), e.getSeq(), e.getNonce(), e.getPrevHash());
            if (!recomputed.equals(e.getHash())) {
                problems.add("Event " + e.getSeq() + ": stored hash does not match its contents (tampered)");
            }
            Checkpoint cp = cache.computeIfAbsent(e.getCheckpointId(), id -> checkpoints.findById(id).orElse(null));
            if (cp == null || cp.getPublicKey() == null
                    || !signatures.verify(keys.publicKey(cp.getPublicKey()), e.getHash(), e.getSignature())) {
                problems.add("Event " + e.getSeq() + ": signature is invalid");
            }
            expectedPrev = e.getHash();
            expectedSeq = e.getSeq() + 1;
        }
        return new ChainReport(tagId, problems.isEmpty(), chain.size(), problems);
    }

    static String computeHash(String tagId, long checkpointId, ScanEvent.Type type, Instant at,
                              int seq, String nonce, String prevHash) {
        return HashUtil.sha256Hex(String.join("|", tagId, String.valueOf(checkpointId), type.name(),
                at.toString(), String.valueOf(seq), nonce, prevHash));
    }
}
