package com.traceguard.verify;

import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.ledger.LedgerService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HASH_MISMATCH, LINK_BROKEN, SEQUENCE_GAP and SIGNATURE_INVALID: the ledger's own tamper checks.
 * Any of them means the recorded history cannot be trusted, so each one is COUNTERFEIT.
 */
@Component
@Order(30)
public class ChainIntegrityCheck implements VerificationCheck {

    private final LedgerService ledger;

    public ChainIntegrityCheck(LedgerService ledger) {
        this.ledger = ledger;
    }

    @Override
    public List<Reason> check(VerificationContext ctx) {
        return ledger.checkChain(ctx.tagId()).issues().stream()
                .map(i -> new Reason(i.code(), Result.COUNTERFEIT, "Event " + i.seq() + ": " + i.message(), i.seq()))
                .toList();
    }
}
