-- Test-only FK constraint for repository integration tests.
ALTER TABLE product
    ADD CONSTRAINT fk_product_brand_id_test
    FOREIGN KEY (brand_id) REFERENCES brand(id);
