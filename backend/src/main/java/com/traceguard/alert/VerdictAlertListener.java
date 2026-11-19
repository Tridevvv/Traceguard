package com.traceguard.alert;

import com.traceguard.alert.Alert.Severity;
import com.traceguard.common.Verdicts.Result;
import com.traceguard.verify.VerdictIssued;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns verification findings into alerts (Observer of {@link VerdictIssued}): COUNTERFEIT reasons raise HIGH
 * alerts, which are also sent to the notifiers, and SUSPECT reasons raise MEDIUM ones. Informational reasons
 * (no impact) never raise an alert.
 */
@Component
public class VerdictAlertListener {

    private final AlertService alerts;

    public VerdictAlertListener(AlertService alerts) {
        this.alerts = alerts;
    }

    @EventListener
    public void on(VerdictIssued event) {
        if (!event.raiseAlerts()) return;
        String tagId = event.verdict().tagId();
        event.verdict().reasons().stream().filter(r -> r.impact() != null).forEach(r -> {
            Severity severity = r.impact() == Result.COUNTERFEIT ? Severity.HIGH : Severity.MEDIUM;
            alerts.raise(tagId, r.code(), severity, r.message());
        });
    }
}
