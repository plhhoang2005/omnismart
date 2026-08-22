CREATE TABLE product (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(5000),
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    inventory_quantity INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_store FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_store_sku UNIQUE (store_id, sku),
    CONSTRAINT ck_product_sku_normalized CHECK (sku = UPPER(TRIM(sku))),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_inventory CHECK (inventory_quantity >= 0),
    CONSTRAINT ck_product_currency CHECK (currency IN ('VND', 'USD')),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_product_store ON product (store_id);
CREATE INDEX idx_product_store_status ON product (store_id, status);
CREATE INDEX idx_product_store_sku ON product (store_id, sku);

CREATE TABLE product_media (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    product_id UUID,
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(32),
    byte_size BIGINT,
    status VARCHAR(16) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attached_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_product_media_store FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_media_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_media_object_key UNIQUE (object_key),
    CONSTRAINT ck_product_media_status CHECK (status IN ('TEMPORARY', 'ATTACHED')),
    CONSTRAINT ck_product_media_type CHECK (
        content_type IS NULL OR content_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
    CONSTRAINT ck_product_media_size CHECK (byte_size IS NULL OR byte_size > 0),
    CONSTRAINT ck_product_media_lifecycle CHECK (
        (status = 'TEMPORARY' AND product_id IS NULL AND content_type IS NULL
            AND byte_size IS NULL AND attached_at IS NULL AND is_primary = FALSE)
        OR
        (status = 'ATTACHED' AND product_id IS NOT NULL AND content_type IS NOT NULL
            AND byte_size IS NOT NULL AND attached_at IS NOT NULL)
    )
);

CREATE INDEX idx_product_media_store_product
    ON product_media (store_id, product_id, status);

ALTER TABLE audit_log DROP CONSTRAINT ck_audit_action;

ALTER TABLE audit_log ADD CONSTRAINT ck_audit_action CHECK (action IN (
    'INVITATION_CREATED',
    'INVITATION_ACCEPTED',
    'INVITATION_DECLINED',
    'INVITATION_EXPIRED',
    'MEMBER_ROLE_CHANGED',
    'MEMBER_REVOKED',
    'PRODUCT_CREATED',
    'PRODUCT_UPDATED',
    'PRODUCT_ARCHIVED',
    'PRODUCT_MEDIA_ATTACHED',
    'PRODUCT_PRIMARY_MEDIA_CHANGED'
));
