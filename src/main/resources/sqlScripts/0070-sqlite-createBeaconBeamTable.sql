
CREATE TABLE BeaconBeam(
    -- compound primary key
     BlockPosX INT NOT NULL
    ,BlockPosY INT NOT NULL
    ,BlockPosZ INT NOT NULL
    
    ,ColorR INT NOT NULL
    ,ColorG INT NOT NULL
    ,ColorB INT NOT NULL
    
    ,LastModifiedUnixDateTime BIGINT NOT NULL -- in GMT 0
    ,CreatedUnixDateTime BIGINT NOT NULL -- in GMT 0
    
    ,PRIMARY KEY (BlockPosX, BlockPosY, BlockPosZ)
);
