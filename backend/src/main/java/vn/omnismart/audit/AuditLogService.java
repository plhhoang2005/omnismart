package vn.omnismart.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            UUID storeId,
            UUID actorUserId,
            AuditAction action,
            String resourceType,
            UUID resourceId,
            String details) {
        repository.save(new AuditLog(
                UUID.randomUUID(),
                storeId,
                actorUserId,
                action,
                resourceType,
                resourceId,
                details));
    }
}
