package com.seibel.distanthorizons.core.file.fullDatafile;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV1;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV1DTO;
import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV1Repo;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

public class FullDataSourceProviderV1<TDhLevel extends IDhLevel>
		implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	protected final ReentrantLock closeLock = new ReentrantLock();
	protected volatile boolean isShutdown = false;
	
	protected final TDhLevel level;
	protected final File saveDir;
	
	public final FullDataSourceV1Repo repo;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceProviderV1(TDhLevel level, ISaveStructure saveStructure, @Nullable File saveDirOverride)
	{
		this.level = level;
		this.saveDir = (saveDirOverride == null) ? saveStructure.getSaveFolder(level.getLevelWrapper()) : saveDirOverride;
		if (!this.saveDir.exists() && !this.saveDir.mkdirs())
		{
			LOGGER.warn("Unable to create full data folder, file saving may fail.");
		}
		
		this.repo = this.createRepo();
	}
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	/** When this is called the parent folders should be created */
	protected FullDataSourceV1Repo createRepo()
	{
		try
		{
			return new FullDataSourceV1Repo(AbstractDhRepo.DEFAULT_DATABASE_TYPE, new File(this.saveDir.getPath() + File.separator + ISaveStructure.DATABASE_NAME));
		}
		catch (SQLException e)
		{
			// should only happen if there is an issue with the database (it's locked or can't be created if missing) 
			// or the database update failed
			throw new RuntimeException(e);
		}
	}
	
	protected FullDataSourceV1 createDataSourceFromDto(FullDataSourceV1DTO dto) throws InterruptedException, IOException, DataCorruptedException
	{
		FullDataSourceV1 dataSource = FullDataSourceV1.createEmpty(dto.pos);
		dataSource.populateFromStream(dto, dto.getInputStream(), this.level);
		return dataSource;
	}
	
	
	
	//==============//
	// data reading //
	//==============//
	
	/**
	 * Returns the {@link FullDataSourceV1} for the given section position. <Br>
	 * The returned data source may be null if there was a problem. <Br> <Br>
	 *
	 * This call is concurrent. I.e. it supports being called by multiple threads at the same time.
	 */
	public CompletableFuture<FullDataSourceV1> getAsync(long pos)
	{
		AbstractExecutorService executor = ThreadPoolUtil.getFileHandlerExecutor();
		if (executor == null || executor.isTerminated())
		{
			return CompletableFuture.completedFuture(null);
		}
		
		try
		{
			return CompletableFuture.supplyAsync(() -> this.get(pos), executor);
		}
		catch (RejectedExecutionException ignore)
		{
			// the thread pool was probably shut down because it's size is being changed, just wait a sec and it should be back
			return CompletableFuture.completedFuture(null);
		}
	}
	/**
	 * Should only be used in internal file handler methods where we are already running on a file handler thread.
	 * Can return null.
	 * @see FullDataSourceProviderV1#getAsync(long)
	 */
	@Nullable
	public FullDataSourceV1 get(Long pos)
	{
		FullDataSourceV1 dataSource = null;
		try (FullDataSourceV1DTO dto = this.repo.getByKey(pos))
		{
			if (dto != null)
			{
				// load from file
				dataSource = this.createDataSourceFromDto(dto);
			}
		}
		catch (InterruptedException ignore) { }
		catch (DataCorruptedException e)
		{
			// stack trace not included since a lot of corrupt data would cause the log to get quite messy, 
			// and it should be fairly easy to see what the problem was from the message
			LOGGER.warn("Corrupted data found at pos ["+ DhSectionPos.toString(pos)+"]. Data at position will be deleted so it can be re-generated and to prevent future issues. Error: "+e.getMessage());
			this.repo.deleteWithKey(pos);
		}
		catch (IOException e)
		{
			LOGGER.warn("File read Error for pos ["+ DhSectionPos.toString(pos)+"], error: "+e.getMessage(), e);
		}
		
		return dataSource;
	}
	
	
	
	//===========//
	// migration //
	//===========//
	
	public long getDataSourceMigrationCount() { return this.repo.getMigrationCount(); }
	
	public ArrayList<FullDataSourceV1> getDataSourcesToMigrate(int limit)
	{
		ArrayList<FullDataSourceV1> dataSourceList = new ArrayList<>();
		
		LongArrayList migrationPosList = this.repo.getPositionsToMigrate(limit);
		for (int i = 0; i < migrationPosList.size(); i++)
		{
			Long pos = migrationPosList.getLong(i);
			FullDataSourceV1 dataSource = this.get(pos);
			if (dataSource != null)
			{
				dataSourceList.add(dataSource);
			}
		}
		
		return dataSourceList;
	}
	
	public void markMigrationFailed(long pos) { ((FullDataSourceV1Repo) this.repo).markMigrationFailed(pos); }
	
	
	
	//=========//
	// cleanup //
	//=========//
	
	@Override
	public void close()
	{
		try
		{
			this.closeLock.lock();
			this.isShutdown = true;
			
			LOGGER.info("Closing [" + this.getClass().getSimpleName() + "] for level: [" + this.level + "].");
			this.repo.close();
		}
		finally
		{
			this.closeLock.unlock();
		}
	}
	
}
