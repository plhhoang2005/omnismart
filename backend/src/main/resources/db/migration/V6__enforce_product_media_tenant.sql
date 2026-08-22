ALTER TABLE product
    ADD CONSTRAINT uq_product_id_store UNIQUE (id, store_id);

ALTER TABLE product_media
    DROP CONSTRAINT fk_product_media_product;

ALTER TABLE product_media
    ADD CONSTRAINT fk_product_media_product_store
        FOREIGN KEY (product_id, store_id)
        REFERENCES product (id, store_id)
        ON DELETE CASCADE;
