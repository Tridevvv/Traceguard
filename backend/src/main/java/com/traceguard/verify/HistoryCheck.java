package com.traceguard.verify;

import com.traceguard.common.Verdicts.Reason;
import com.traceguard.common.Verdicts.Result;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** NO_HISTORY: a tag that was issued but never scanned cannot have its journey confirmed. */
@Component
@Order(20)
public class HistoryCheck implements VerificationCheck {

    @Override
    public List<Reason> check(VerificationContext ctx) {
        if (!ctx.events().isEmpty()) return List.of();
        return List.of(new Reason("NO_HISTORY", Result.SUSPECT,
                "No scans have been recorded for this tag, so its journey cannot be confirmed", null));
    }
}
