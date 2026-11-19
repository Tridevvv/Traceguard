package com.traceguard.verify;

import com.traceguard.common.Verdicts.Reason;

import java.util.List;

/**
 * One rule of the verification engine (Strategy pattern). Each implementation is a Spring bean, and
 * {@link VerificationService} runs every bean in {@code @Order} order, so a new check is added by writing a
 * class, without changing the service. The verdict is the worst impact among all reasons returned.
 */
public interface VerificationCheck {

    /** Returns the problems found for one registered tag; an empty list means the check passed. */
    List<Reason> check(VerificationContext ctx);
}
