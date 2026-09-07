package vn.omnismart.content;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogService;
import vn.omnismart.catalog.Product;
import vn.omnismart.catalog.ProductRepository;
import vn.omnismart.catalog.ProductStatus;
import vn.omnismart.store.StoreAuthorizationService;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreOperationGuard;

@Service
public class ContentWorkflowService {

    private static final int MAX_BODY_LENGTH = 10_000;

    private final ContentItemRepository itemRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentApprovalRepository approvalRepository;
    private final ProductRepository productRepository;
    private final StoreAuthorizationService authorizationService;
    private final StoreOperationGuard storeOperationGuard;
    private final AuditLogService auditLogService;

    public ContentWorkflowService(
            ContentItemRepository itemRepository,
            ContentVersionRepository versionRepository,
            ContentApprovalRepository approvalRepository,
            ProductRepository productRepository,
            StoreAuthorizationService authorizationService,
            StoreOperationGuard storeOperationGuard,
            AuditLogService auditLogService) {
        this.itemRepository = itemRepository;
        this.versionRepository = versionRepository;
        this.approvalRepository = approvalRepository;
        this.productRepository = productRepository;
        this.authorizationService = authorizationService;
        this.storeOperationGuard = storeOperationGuard;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ContentResponse create(
            OidcUser principal,
            UUID storeId,
            UUID productId,
            ContentChannel channel,
            String rawBody) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        storeOperationGuard.requireOperational(storeId);
        requireActiveProduct(storeId, productId);
        String body = normalizeBody(rawBody);

        ContentItem item = new ContentItem(
                UUID.randomUUID(), storeId, productId, channel, actor.getUserId());
        ContentVersion contentVersion = new ContentVersion(
                UUID.randomUUID(), storeId, item.getId(), 1, body, actor.getUserId());
        itemRepository.saveAndFlush(item);
        versionRepository.saveAndFlush(contentVersion);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.CONTENT_CREATED,
                "CONTENT",
                item.getId(),
                "channel=" + channel + ",versionNumber=1");
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.CONTENT_VERSION_CREATED,
                "CONTENT_VERSION",
                contentVersion.getId(),
                "contentItemId=" + item.getId() + ",versionNumber=1");
        return response(item, contentVersion);
    }

    @Transactional(readOnly = true)
    public ContentPageResponse list(
            OidcUser principal,
            UUID storeId,
            UUID productId,
            ContentStatus status,
            ContentChannel channel,
            int page,
            int size) {
        authorizationService.requireMembership(principal, storeId);
        Page<ContentItem> items = itemRepository.search(
                storeId,
                productId,
                status,
                channel,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "updatedAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new ContentPageResponse(
                items.getContent().stream().map(this::summary).toList(),
                items.getNumber(),
                items.getSize(),
                items.getTotalElements(),
                items.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ContentResponse get(OidcUser principal, UUID storeId, UUID contentId) {
        authorizationService.requireMembership(principal, storeId);
        ContentItem item = requireItem(storeId, contentId);
        return response(item, requireCurrentVersion(item));
    }

    @Transactional(readOnly = true)
    public List<ContentVersionResponse> versions(
            OidcUser principal,
            UUID storeId,
            UUID contentId) {
        authorizationService.requireMembership(principal, storeId);
        requireItem(storeId, contentId);
        return versionRepository
                .findByContentItemIdAndStoreIdOrderByVersionNumberDesc(contentId, storeId)
                .stream()
                .map(this::versionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> approvals(
            OidcUser principal,
            UUID storeId,
            UUID contentId) {
        authorizationService.requireMembership(principal, storeId);
        requireItem(storeId, contentId);
        return approvalRepository
                .findByContentItemIdAndStoreIdOrderBySubmittedAtDesc(contentId, storeId)
                .stream()
                .map(this::approvalResponse)
                .toList();
    }

    @Transactional
    public ContentResponse edit(
            OidcUser principal,
            UUID storeId,
            UUID contentId,
            long expectedVersion,
            String rawBody) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        storeOperationGuard.requireOperational(storeId);
        ContentItem item = requireItem(storeId, contentId);
        requireVersion(item, expectedVersion);
        requireActiveProduct(storeId, item.getProductId());
        if (item.getStatus() != ContentStatus.DRAFT
                && item.getStatus() != ContentStatus.REJECTED) {
            throw ContentException.invalidTransition(
                    "Only draft or rejected content can be edited");
        }
        String body = normalizeBody(rawBody);
        if (requireCurrentVersion(item).getBody().equals(body)) {
            throw ContentException.unchanged();
        }

        int versionNumber = item.edit();
        flushItem(item);
        ContentVersion contentVersion = new ContentVersion(
                UUID.randomUUID(),
                storeId,
                contentId,
                versionNumber,
                body,
                actor.getUserId());
        try {
            versionRepository.saveAndFlush(contentVersion);
        } catch (DataIntegrityViolationException exception) {
            throw ContentException.versionConflict();
        }
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.CONTENT_VERSION_CREATED,
                "CONTENT_VERSION",
                contentVersion.getId(),
                "contentItemId=" + contentId + ",versionNumber=" + versionNumber);
        return response(item, contentVersion);
    }

    @Transactional
    public ContentResponse submit(
            OidcUser principal,
            UUID storeId,
            UUID contentId,
            long expectedVersion) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        storeOperationGuard.requireOperational(storeId);
        ContentItem item = requireItem(storeId, contentId);
        requireVersion(item, expectedVersion);
        requireActiveProduct(storeId, item.getProductId());
        if (item.getStatus() != ContentStatus.DRAFT) {
            throw ContentException.invalidTransition("Only draft content can be submitted");
        }
        ContentVersion currentVersion = requireCurrentVersion(item);

        item.submit();
        flushItem(item);
        ContentApproval approval = new ContentApproval(
                UUID.randomUUID(),
                storeId,
                contentId,
                currentVersion.getId(),
                actor.getUserId());
        try {
            approvalRepository.saveAndFlush(approval);
        } catch (DataIntegrityViolationException exception) {
            throw ContentException.invalidTransition(
                    "The current content version has already been submitted");
        }
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.CONTENT_SUBMITTED,
                "CONTENT",
                contentId,
                "versionNumber=" + item.getCurrentVersionNumber());
        return response(item, currentVersion);
    }

    @Transactional
    public ContentResponse approve(
            OidcUser principal,
            UUID storeId,
            UUID contentId,
            long expectedVersion) {
        StoreMember actor = authorizationService.requireOwner(principal, storeId);
        storeOperationGuard.requireOperational(storeId);
        ContentItem item = requireItem(storeId, contentId);
        requireVersion(item, expectedVersion);
        requireActiveProduct(storeId, item.getProductId());
        requireInReview(item);
        ContentApproval approval = requirePendingApproval(storeId, contentId);

        item.approve();
        flushItem(item);
        approval.approve(actor.getUserId());
        approvalRepository.saveAndFlush(approval);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.CONTENT_APPROVED,
                "CONTENT",
                contentId,
                "versionNumber=" + item.getCurrentVersionNumber());
        return response(item, requireCurrentVersion(item));
    }

    @Transactional
    public ContentResponse reject(
            OidcUser principal,
            UUID storeId,
            UUID contentId,
            long expectedVersion,
            String rawReason) {
        StoreMember actor = authorizationService.requireOwner(principal, storeId);
        storeOperationGuard.requireOperational(storeId);
        ContentItem item = requireItem(storeId, contentId);
        requireVersion(item, expectedVersion);
        requireInReview(item);
        ContentApproval approval = requirePendingApproval(storeId, contentId);
        String reason = normalizeReason(rawReason);

        item.reject();
        flushItem(item);
        approval.reject(actor.getUserId(), reason);
        approvalRepository.saveAndFlush(approval);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.CONTENT_REJECTED,
                "CONTENT",
                contentId,
                "versionNumber=" + item.getCurrentVersionNumber());
        return response(item, requireCurrentVersion(item));
    }

    private ContentItem requireItem(UUID storeId, UUID contentId) {
        return itemRepository.findByIdAndStoreId(contentId, storeId)
                .orElseThrow(ContentException::notFound);
    }

    private Product requireActiveProduct(UUID storeId, UUID productId) {
        Product product = productRepository.findLockedByIdAndStoreId(productId, storeId)
                .orElseThrow(ContentException::productNotFound);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw ContentException.productInactive();
        }
        return product;
    }

    private ContentVersion requireCurrentVersion(ContentItem item) {
        return versionRepository.findByContentItemIdAndStoreIdAndVersionNumber(
                        item.getId(), item.getStoreId(), item.getCurrentVersionNumber())
                .orElseThrow(() -> new IllegalStateException("Current content version is missing"));
    }

    private ContentApproval requirePendingApproval(UUID storeId, UUID contentId) {
        return approvalRepository
                .findFirstByContentItemIdAndStoreIdAndStatusOrderBySubmittedAtDesc(
                        contentId, storeId, ApprovalStatus.PENDING)
                .orElseThrow(ContentException::approvalNotFound);
    }

    private void requireVersion(ContentItem item, long expectedVersion) {
        if (item.getVersion() != expectedVersion) {
            throw ContentException.versionConflict();
        }
    }

    private void requireInReview(ContentItem item) {
        if (item.getStatus() != ContentStatus.IN_REVIEW) {
            throw ContentException.invalidTransition("Only content in review can be reviewed");
        }
    }

    private void flushItem(ContentItem item) {
        try {
            itemRepository.saveAndFlush(item);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw ContentException.versionConflict();
        }
    }

    private String normalizeBody(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_BODY_LENGTH) {
            throw ContentException.bodyInvalid();
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw ContentException.rejectionReasonInvalid();
        }
        return normalized;
    }

    private ContentSummaryResponse summary(ContentItem item) {
        return new ContentSummaryResponse(
                item.getId(),
                item.getStoreId(),
                item.getProductId(),
                item.getChannel(),
                item.getStatus(),
                item.getCurrentVersionNumber(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private ContentResponse response(ContentItem item, ContentVersion currentVersion) {
        return new ContentResponse(
                item.getId(),
                item.getStoreId(),
                item.getProductId(),
                item.getChannel(),
                item.getStatus(),
                item.getCurrentVersionNumber(),
                item.getVersion(),
                item.getCreatedByUserId(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                versionResponse(currentVersion));
    }

    private ContentVersionResponse versionResponse(ContentVersion version) {
        return new ContentVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getBody(),
                version.getCreatedByUserId(),
                version.getCreatedAt());
    }

    private ApprovalResponse approvalResponse(ContentApproval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getSubmittedVersionId(),
                approval.getStatus(),
                approval.getSubmittedByUserId(),
                approval.getReviewedByUserId(),
                approval.getRejectionReason(),
                approval.getSubmittedAt(),
                approval.getReviewedAt());
    }

    public record ContentResponse(
            UUID id,
            UUID storeId,
            UUID productId,
            ContentChannel channel,
            ContentStatus status,
            int currentVersionNumber,
            long version,
            UUID createdByUserId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            ContentVersionResponse currentVersion) {

        @Override
        public String toString() {
            return "ContentResponse[id=" + id + ", storeId=" + storeId
                    + ", productId=" + productId + ", channel=" + channel
                    + ", status=" + status + ", currentVersionNumber=" + currentVersionNumber
                    + ", version=" + version + ", currentVersion=[REDACTED]]";
        }
    }

    public record ContentSummaryResponse(
            UUID id,
            UUID storeId,
            UUID productId,
            ContentChannel channel,
            ContentStatus status,
            int currentVersionNumber,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record ContentPageResponse(
            List<ContentSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record ContentVersionResponse(
            UUID id,
            int versionNumber,
            String body,
            UUID createdByUserId,
            OffsetDateTime createdAt) {

        @Override
        public String toString() {
            return "ContentVersionResponse[id=" + id + ", versionNumber=" + versionNumber
                    + ", body=[REDACTED], createdByUserId=" + createdByUserId
                    + ", createdAt=" + createdAt + "]";
        }
    }

    public record ApprovalResponse(
            UUID id,
            UUID submittedVersionId,
            ApprovalStatus status,
            UUID submittedByUserId,
            UUID reviewedByUserId,
            String rejectionReason,
            OffsetDateTime submittedAt,
            OffsetDateTime reviewedAt) {

        @Override
        public String toString() {
            return "ApprovalResponse[id=" + id + ", submittedVersionId=" + submittedVersionId
                    + ", status=" + status + ", submittedByUserId=" + submittedByUserId
                    + ", reviewedByUserId=" + reviewedByUserId
                    + ", rejectionReason=[REDACTED], submittedAt=" + submittedAt
                    + ", reviewedAt=" + reviewedAt + "]";
        }
    }
}
