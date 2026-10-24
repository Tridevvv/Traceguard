package com.traceguard.alert;

import java.util.List;

/** Delivers a high-severity alert. The log notifier always runs; email is added when SMTP is configured. */
public interface Notifier {
    void notify(Alert alert, List<String> recipients);
}
