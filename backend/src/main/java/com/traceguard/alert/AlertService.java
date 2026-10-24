package com.traceguard.alert;

import com.traceguard.alert.Alert.Severity;
import com.traceguard.alert.Alert.Status;
import com.traceguard.auth.AppUser;
import com.traceguard.auth.AppUserRepository;
import com.traceguard.auth.Role;
import com.traceguard.catalog.CatalogRepositories.ProductRepository;
import com.traceguard.common.Errors.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AlertService {

    private final AlertRepository alerts;
    private final AppUserRepository users;
    private final ProductRepository products;
    private final List<Notifier> notifiers;

    public AlertService(AlertRepository alerts, AppUserRepository users, ProductRepository products, List<Notifier> notifiers) {
        this.alerts = alerts;
        this.users = users;
        this.products = products;
        this.notifiers = notifiers;
    }

    /**
     * Raises an alert unless an OPEN alert with the same tag and code already exists (so repeated
     * scans do not flood the center). HIGH alerts are also sent to the manufacturer and admins.
     */
    @Transactional
    public Alert raise(String tagId, String code, Severity severity, String message) {
        String tag = tagId.length() > 64 ? tagId.substring(0, 64) : tagId;
        Optional<Alert> existing = alerts.findFirstByTagIdAndCodeAndStatus(tag, code, Status.OPEN);
        if (existing.isPresent()) return existing.get();

        Alert alert = alerts.save(new Alert(tag, code, severity, message));
        if (severity == Severity.HIGH) {
            List<String> to = recipientsFor(tag);
            notifiers.forEach(n -> n.notify(alert, to));
        }
        return alert;
    }

    /** Same as {@link #raise} but commits on its own, so it survives when the caller's transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Alert raiseIsolated(String tagId, String code, Severity severity, String message) {
        return raise(tagId, code, severity, message);
    }

    /** Alerts the user may see: admins and auditors see all; a manufacturer sees alerts on its own products. */
    @Transactional(readOnly = true)
    public List<Alert> visibleTo(AppUser user, Status status) {
        List<Alert> all = status == null ? alerts.findAllByOrderByIdDesc() : alerts.findByStatusOrderByIdDesc(status);
        if (user.getRole() != Role.MANUFACTURER) return all;
        return all.stream().filter(a -> ownedBy(user, a)).toList();
    }

    @Transactional
    public Alert updateStatus(AppUser user, Long id, Status status) {
        Alert a = alerts.findById(id).orElseThrow(() -> new NotFoundException("Alert " + id + " not found"));
        if (user.getRole() == Role.MANUFACTURER && !ownedBy(user, a)) {
            throw new NotFoundException("Alert " + id + " not found");
        }
        a.setStatus(status);
        return a;
    }

    private boolean ownedBy(AppUser user, Alert a) {
        return user.getOrganizationId() != null
                && products.organizationOfTag(a.getTagId()).filter(user.getOrganizationId()::equals).isPresent();
    }

    private List<String> recipientsFor(String tagId) {
        Set<String> emails = new LinkedHashSet<>();
        users.findByRoleAndEnabledTrue(Role.ADMIN).forEach(u -> emails.add(u.getEmail()));
        products.organizationOfTag(tagId).ifPresent(org ->
                users.findByRoleAndOrganizationIdAndEnabledTrue(Role.MANUFACTURER, org).forEach(u -> emails.add(u.getEmail())));
        return new ArrayList<>(emails);
    }
}
