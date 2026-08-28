-- Plant archive cultivation records migration
-- A plant may have multiple cultivation operations in the same year and month.
-- This changes only the obsolete uniqueness constraint; existing rows are retained.
USE jhds;

SET @cultivation_unique_key = (
    SELECT DISTINCT CONCAT('ALTER TABLE plant_cultivation DROP INDEX `', INDEX_NAME, '`')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'plant_cultivation'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
      AND COLUMN_NAME = 'plant_id'
      AND INDEX_NAME IN (
          SELECT INDEX_NAME
          FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'plant_cultivation'
            AND COLUMN_NAME = 'month'
      )
    LIMIT 1
);
SET @cultivation_unique_key = IFNULL(@cultivation_unique_key, 'SELECT 1');
PREPARE cultivation_drop_stmt FROM @cultivation_unique_key;
EXECUTE cultivation_drop_stmt;
DEALLOCATE PREPARE cultivation_drop_stmt;
