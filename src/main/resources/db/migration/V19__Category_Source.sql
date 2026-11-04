ALTER TABLE subscriptions ADD COLUMN category_source VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (category_source IN ('DETECTED','USER','UNKNOWN'));
ALTER TABLE subscriptions ADD COLUMN category_confidence VARCHAR(6)
    CHECK (category_confidence IN ('HIGH','MEDIUM','LOW'));
