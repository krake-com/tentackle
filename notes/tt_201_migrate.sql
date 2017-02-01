alter table prefnode add column rootnodeid bigint;
update prefnode set rootnodeid=0;
alter table prefnode alter rootnodeid set not null;
comment on column prefnode.rootnodeid is 'ID of the root node';
create index prefnode_root on prefnode (rootnodeid);

alter table prefkey add column rootnodeid bigint;
update prefkey set rootnodeid=0;
alter table prefkey alter rootnodeid set not null;
comment on column prefkey.rootnodeid is 'ID of the root node';
create index prefkey_root on prefkey (rootnodeid);

