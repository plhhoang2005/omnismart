CREATE TABLE content_item (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    product_id UUID NOT NULL,
    channel VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_version_number INTEGER NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_item_store FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE CASCADE,
    CONSTRAINT fk_content_item_product_store FOREIGN KEY (product_id, store_id)
        REFERENCES product (id, store_id) ON DELETE CASCADE,
    CONSTRAINT fk_content_item_creator FOREIGN KEY (created_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uq_content_item_id_store UNIQUE (id, store_id),
    CONSTRAINT ck_content_item_channel CHECK (channel IN ('FACEBOOK', 'TIKTOK', 'MARKETPLACE')),
    CONSTRAINT ck_content_item_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_content_item_version_number CHECK (current_version_number >= 1)
);

CREATE INDEX idx_content_item_store_status_updated
    ON content_item (store_id, status, updated_at);
CREATE INDEX idx_content_item_store_product
    ON content_item (store_id, product_id);

CREATE TABLE content_version (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    content_item_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    body VARCHAR(10000) NOT NULL,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_version_item_store FOREIGN KEY (content_item_id, store_id)
        REFERENCES content_item (id, store_id) ON DELETE CASCADE,
    CONSTRAINT fk_content_version_creator FOREIGN KEY (created_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uq_content_version_item_number UNIQUE (content_item_id, version_number),
    CONSTRAINT uq_content_version_id_store_item UNIQUE (id, store_id, content_item_id),
    CONSTRAINT ck_content_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_content_version_body CHECK (CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 10000)
);

CREATE INDEX idx_content_version_item_number
    ON content_version (store_id, content_item_id, version_number);

CREATE TABLE content_approval (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    content_item_id UUID NOT NULL,
    submitted_version_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    submitted_by_user_id UUID,
    reviewed_by_user_id UUID,
    rejection_reason VARCHAR(1000),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_content_approval_item_store FOREIGN KEY (content_item_id, store_id)
        REFERENCES content_item (id, store_id) ON DELETE CASCADE,
    CONSTRAINT fk_content_approval_version_store_item
        FOREIGN KEY (submitted_version_id, store_id, content_item_id)
        REFERENCES content_version (id, store_id, content_item_id) ON DELETE CASCADE,
    CONSTRAINT fk_content_approval_submitter FOREIGN KEY (submitted_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_content_approval_reviewer FOREIGN KEY (reviewed_by_user_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uq_content_approval_item_version UNIQUE (content_item_id, submitted_version_id),
    CONSTRAINT ck_content_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_content_approval_rejection_reason CHECK (
        rejection_reason IS NULL OR CHAR_LENGTH(TRIM(rejection_reason)) BETWEEN 1 AND 1000
    ),
    CONSTRAINT ck_content_approval_lifecycle CHECK (
        (status = 'PENDING' AND reviewed_by_user_id IS NULL
            AND reviewed_at IS NULL AND rejection_reason IS NULL)
        OR
        (status = 'APPROVED' AND reviewed_at IS NOT NULL AND rejection_reason IS NULL)
        OR
        (status = 'REJECTED' AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL)
    )
);

CREATE INDEX idx_content_approval_store_status_submitted
    ON content_approval (store_id, status, submitted_at);
CREATE INDEX idx_content_approval_item_submitted
    ON content_approval (store_id, content_item_id, submitted_at);

ALTER TABLE audit_log DROP CONSTRAINT ck_audit_action;

ALTER TABLE audit_log ADD CONSTRAINT ck_audit_action CHECK (action IN (
    'STORE_CREATED',
    'STORE_ONBOARDING_COMPLETED',
    'STORE_UPDATED',
    'STORE_ARCHIVED',
    'STORE_REACTIVATED',
    'INVITATION_CREATED',
    'INVITATION_ACCEPTED',
    'INVITATION_DECLINED',
    'INVITATION_EXPIRED',
    'INVITATION_REVOKED',
    'MEMBER_ROLE_CHANGED',
    'MEMBER_REVOKED',
    'PRODUCT_CREATED',
    'PRODUCT_UPDATED',
    'PRODUCT_ARCHIVED',
    'PRODUCT_MEDIA_ATTACHED',
    'PRODUCT_PRIMARY_MEDIA_CHANGED',
    'PRODUCT_MEDIA_DELETED',
    'CONTENT_CREATED',
    'CONTENT_VERSION_CREATED',
    'CONTENT_SUBMITTED',
    'CONTENT_APPROVED',
    'CONTENT_REJECTED'
));
