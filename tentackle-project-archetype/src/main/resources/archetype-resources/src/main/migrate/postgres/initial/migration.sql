CREATE TABLE migration (
    version VARCHAR(10),
    since TIMESTAMP,
    comment VARCHAR(1024)
);
COMMENT ON TABLE migration IS 'migration history';
COMMENT ON COLUMN migration.version IS 'deployment date';
COMMENT ON COLUMN migration.since IS 'deployment date';
COMMENT ON COLUMN migration.comment IS 'additional comments';

INSERT INTO migration (version, since, comment) VALUES ('1.0', '2017-11-21', 'project start');



