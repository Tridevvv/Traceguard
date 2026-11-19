package com.traceguard.verify;

import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** BATCH_RECALLED: the manufacturer recalled the tag's batch. */
@Component
@Order(10)
public class RecallCheck implements VerificationCheck {

    @Override
    public List<Reason> check(VerificationContext ctx) {
        if (!ctx.batch().isRecalled()) return List.of();
        return List.of(new Reason("BATCH_RECALLED", Result.SUSPECT,
                "Batch " + ctx.batch().getLabel() + " has been recalled by the manufacturer", null));
    }
}
