package com.traceguard.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Records an action by the currently authenticated user. */
    public void record(String action, String target) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        recordAs(auth == null ? "anonymous" : String.valueOf(auth.getPrincipal()), action, target);
    }

    public void recordAs(String userEmail, String action, String target) {
        repo.save(new AuditLog(userEmail, action, target));
    }

    public List<AuditLog> latest(int size) {
        return repo.findAllByOrderByIdDesc(PageRequest.of(0, Math.min(Math.max(size, 1), 500))).getContent();
    }

    @RestController
    @RequestMapping("/api/audit-log")
    static class AuditController {
        private final AuditService service;

        AuditController(AuditService service) {
            this.service = service;
        }

        public record Entry(Long id, String user, String action, String target, java.time.Instant at) {}

        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
        public List<Entry> latest(@RequestParam(defaultValue = "100") int size) {
            return service.latest(size).stream()
                    .map(a -> new Entry(a.getId(), a.getUserEmail(), a.getAction(), a.getTarget(), a.getCreatedAt()))
                    .toList();
        }
    }
}
