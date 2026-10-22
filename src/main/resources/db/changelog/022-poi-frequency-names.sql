-- The original inline CHECK has an H2-generated name; locate it through metadata.
EXECUTE IMMEDIATE 'ALTER TABLE points_of_interest DROP CONSTRAINT "' ||
    (SELECT tc.constraint_name FROM information_schema.table_constraints tc
     JOIN information_schema.check_constraints cc
       ON cc.constraint_schema = tc.constraint_schema AND cc.constraint_name = tc.constraint_name
     WHERE tc.table_schema = CURRENT_SCHEMA AND tc.table_name = 'POINTS_OF_INTEREST'
       AND tc.constraint_type = 'CHECK' AND cc.check_clause LIKE '%REGEXP_LIKE%') || '"';
ALTER TABLE points_of_interest ADD CONSTRAINT ck_poi_name
    CHECK (REGEXP_LIKE(name, '^([A-Z0-9]{1,9}|[0-9]{3}[.][0-9]{1,3}(-[A-Za-z0-9]{0,2})?)$'));
ALTER TABLE points_of_interest ADD COLUMN name_key VARCHAR(9) GENERATED ALWAYS AS (UPPER(name));
ALTER TABLE points_of_interest ADD CONSTRAINT uk_poi_name_case_insensitive UNIQUE(name_key);
