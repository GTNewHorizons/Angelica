
CREATE TABLE ChunkHash(
    -- compound primary key
     ChunkPosX INT NOT NULL
    ,ChunkPosZ INT NOT NULL
    
    ,ChunkHash INT NOT NULL
    
    ,LastModifiedUnixDateTime BIGINT NOT NULL -- in GMT 0
    ,CreatedUnixDateTime BIGINT NOT NULL -- in GMT 0
    
    ,PRIMARY KEY (ChunkPosX, ChunkPosZ)
);
