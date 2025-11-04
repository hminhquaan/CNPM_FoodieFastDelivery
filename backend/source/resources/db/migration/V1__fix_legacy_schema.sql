-- Normalize legacy schema to match JPA entities
-- - Drop unused legacy column `users.password`
-- - Align id/user_id/role_id columns to BIGINT for FK compatibility
-- - Do changes with FK checks disabled to avoid transient mismatch errors

SET FOREIGN_KEY_CHECKS=0;

-- Ensure primary keys use BIGINT
ALTER TABLE users MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE roles MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

-- Align referencing columns to BIGINT
ALTER TABLE user_address MODIFY COLUMN user_id BIGINT NOT NULL;
ALTER TABLE user_role MODIFY COLUMN user_id BIGINT NOT NULL;
ALTER TABLE user_role MODIFY COLUMN role_id BIGINT NOT NULL;
ALTER TABLE cart MODIFY COLUMN user_id BIGINT NOT NULL;
ALTER TABLE orders MODIFY COLUMN user_id BIGINT NOT NULL;
ALTER TABLE store MODIFY COLUMN owner_user_id BIGINT NOT NULL;

-- Remove legacy password column if it exists
-- MySQL 8.0 supports IF EXISTS
ALTER TABLE users DROP COLUMN IF EXISTS password;

SET FOREIGN_KEY_CHECKS=1;
