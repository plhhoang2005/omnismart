ALTER TABLE store_member
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE membership_invitation (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    pending_email VARCHAR(320),
    role VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    invited_by_user_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invitation_store FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_inviter FOREIGN KEY (invited_by_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uq_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_invitation_pending_email UNIQUE (store_id, pending_email),
    CONSTRAINT ck_invitation_role CHECK (role IN ('OWNER', 'STAFF')),
    CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT ck_invitation_email_normalized CHECK (email = LOWER(email)),
    CONSTRAINT ck_invitation_lifecycle CHECK (
        (status = 'PENDING' AND pending_email = email AND responded_at IS NULL)
        OR
        (status <> 'PENDING' AND pending_email IS NULL AND responded_at IS NOT NULL)
    )
);

CREATE INDEX idx_invitation_store_created_at
    ON membership_invitation (store_id, created_at);

CREATE INDEX idx_invitation_email_status
    ON membership_invitation (email, status);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    actor_user_id UUID,
    action VARCHAR(48) NOT NULL,
    resource_type VARCHAR(48) NOT NULL,
    resource_id UUID NOT NULL,
    details VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_store FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_audit_action CHECK (action IN (
        'INVITATION_CREATED',
        'INVITATION_ACCEPTED',
        'INVITATION_DECLINED',
        'INVITATION_EXPIRED',
        'MEMBER_ROLE_CHANGED',
        'MEMBER_REVOKED'
    ))
);

CREATE INDEX idx_audit_store_created_at
    ON audit_log (store_id, created_at);
