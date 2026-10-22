-- Tables of the Demo* test PDOs.
--
-- The Demo* PDOs are hand-written equivalents of generated code and deliberately have no model
-- (see src/test/README.md), so there is nothing the SQL maven plugin could derive a DDL from.
-- This script is therefore hand-written too, and it is the one place where the columns, the
-- CN_* constants and the getFields/setFields of the Demo*PersistenceImpl classes must agree.
--
-- Run by AbstractPersistenceDbTest after the generated createttmodel.sql.

CREATE TABLE demoentity (
    name VARCHAR(64),
    id BIGINT NOT NULL PRIMARY KEY,
    serial BIGINT NOT NULL DEFAULT 1
);

-- The component of a demoentity aggregate: root columns, normtext and the token-lock columns.
CREATE TABLE democomponent (
    ctext VARCHAR(64),
    rootid BIGINT NOT NULL DEFAULT 0,
    rootclassid INTEGER NOT NULL DEFAULT 0,
    normtext VARCHAR(256),
    editedby BIGINT NOT NULL DEFAULT 0,
    editedsince TIMESTAMP,
    editedexpiry TIMESTAMP,
    id BIGINT NOT NULL PRIMARY KEY,
    serial BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX democomponent_root ON democomponent (rootid, rootclassid);

-- A root entity with the token-lock columns.
CREATE TABLE demolockable (
    ltext VARCHAR(64),
    editedby BIGINT NOT NULL DEFAULT 0,
    editedsince TIMESTAMP,
    editedexpiry TIMESTAMP,
    id BIGINT NOT NULL PRIMARY KEY,
    serial BIGINT NOT NULL DEFAULT 1
);
