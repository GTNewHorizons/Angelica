
-- significantly speeds up parent update handling
create index FullDataUpdatedIndex on FullData (ApplyToParent) where ApplyToParent = 1
