-- The schema list, and NOTHING else.
--
-- MySQL's entrypoint runs this on the FIRST start of an empty volume — and only then. If you
-- already had a `mysql-data` volume before the sidecar existed, this never ran for you and
-- `sidecar_db` does not exist; the sidecar will say so and tell you to `docker compose down -v`.
--
-- Schema isolation is the rule: every service migrates its OWN schema with Liquibase and never
-- reads another's. They integrate over REST, not through shared tables. The sidecar is held to
-- the same rule even though it is only a development tool.
CREATE DATABASE IF NOT EXISTS neo_03;
CREATE DATABASE IF NOT EXISTS sidecar_db;
