package com.traceguard.verify;

import com.traceguard.common.Verdicts.Verdict;

/**
 * Published after every verification (Observer pattern). The alert center and the analytics history listen
 * for it, so the verification engine does not depend on either of them.
 *
 * @param recordHistory whether the outcome belongs in the analytics history (false for never-issued tags)
 * @param raiseAlerts   whether SUSPECT and COUNTERFEIT reasons should raise alerts
 */
public record VerdictIssued(Verdict verdict, boolean recordHistory, boolean raiseAlerts) {}
