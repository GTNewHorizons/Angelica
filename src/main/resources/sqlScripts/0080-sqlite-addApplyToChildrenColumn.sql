
-- Applying to children is needed to fix a bug with N-sized generation.
-- If we don't fill the whole tree with data, it's possible to render empty/incomplete LODs, which looks bad.
alter table FullData add column ApplyToChildren BIT NULL;

--batch--

-- significantly speeds up update handling
create index FullDataApplyToChildrenIndex on FullData (ApplyToChildren) where ApplyToChildren = 1;
