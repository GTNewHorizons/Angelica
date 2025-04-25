package com.seibel.distanthorizons.forge;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class ForgeServerProxy implements AbstractModInitializer.IEventProxy
{
	private static World GetEventLevel(WorldEvent e) { return e.world; }

	private final ServerApi serverApi = ServerApi.INSTANCE;
	private final boolean isDedicated;



	@Override
	public void registerEvents()
	{
		MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
		if (this.isDedicated)
		{
			ForgePluginPacketSender.setPacketHandler(ServerApi.INSTANCE::pluginMessageReceived);
		}
	}



	//=============//
	// constructor //
	//=============//

	public ForgeServerProxy(boolean isDedicated)
	{
		this.isDedicated = isDedicated;
	}



	//========//
	// events //
	//========//

    public static boolean connected = false;

    private class ChunkLoadEvent
    {
        public final ChunkWrapper chunk;
        public final ILevelWrapper level;
        public int age;

        private ChunkLoadEvent(ChunkWrapper chunk, ILevelWrapper level) {
            this.chunk = chunk;
            this.level = level;
        }
    }

    private static final Queue<ChunkLoadEvent> chunkLoadEvents = new ConcurrentLinkedQueue<>();

	// ServerTickEvent (at end)
	@SubscribeEvent
	public void serverTickEvent(TickEvent.ServerTickEvent event)
	{
        if(event.phase == TickEvent.Phase.END) {
            Iterator<ChunkLoadEvent> iterator = chunkLoadEvents.iterator();
            while (iterator.hasNext()) {
                ChunkLoadEvent chunkLoadEvent = iterator.next();
                if (chunkLoadEvent.chunk.isChunkReady()) {
                    this.serverApi.serverChunkLoadEvent(chunkLoadEvent.chunk, chunkLoadEvent.level);
                    iterator.remove();
                } else {
                    // Cleanup old events if they never got ready
                    chunkLoadEvent.age++;
                    if (chunkLoadEvent.age > 200) {
                        iterator.remove();
                    }
                }
            }

            int count = 0;
            while (!taskQueue.isEmpty()) {
                ScheduledTask<?> scheduledTask = taskQueue.poll();
                if (scheduledTask == null) {
                    continue;
                }
                scheduledTask.run();
                if (scheduledTask.isLimited()) {
                    count++;
                }
                if (count >= AngelicaConfig.distantHorizonsChunkUpdatesPerTick)
                {
                    break;
                }
            }
        }
		if ((connected || isDedicated) && event.phase == TickEvent.Phase.END)
		{
			this.serverApi.serverTickEvent();
		}
	}

	// ServerLevelLoadEvent
	@SubscribeEvent
	public void serverLevelLoadEvent(WorldEvent.Load event)
	{
		if (GetEventLevel(event) instanceof WorldServer)
		{
			this.serverApi.serverLevelLoadEvent(getServerLevelWrapper((WorldServer) GetEventLevel(event)));
		}
	}

	// ServerLevelUnloadEvent
	@SubscribeEvent
	public void serverLevelUnloadEvent(WorldEvent.Unload event)
	{
		if (GetEventLevel(event) instanceof WorldServer)
		{
			this.serverApi.serverLevelUnloadEvent(getServerLevelWrapper((WorldServer) GetEventLevel(event)));
		}
        chunkLoadEvents.removeIf(x -> x.level.getWrappedMcObject() == event.world);
	}

	@SubscribeEvent
	public void serverChunkLoadEvent(ChunkEvent.Load event)
	{
		ILevelWrapper levelWrapper = ProxyUtil.getLevelWrapper(GetEventLevel(event));
		ChunkWrapper chunk = new ChunkWrapper(event.getChunk(), levelWrapper);
        chunkLoadEvents.add(new ChunkLoadEvent(chunk, levelWrapper));
	}

    @SubscribeEvent
    public void serverChunkUnLoadEvent(ChunkDataEvent.Save event)
    {
        ILevelWrapper levelWrapper = ProxyUtil.getLevelWrapper(GetEventLevel(event));
        ChunkWrapper chunk = new ChunkWrapper(event.getChunk(), levelWrapper);
        ServerApi.INSTANCE.serverChunkSaveEvent(chunk, levelWrapper);
    }

	@SubscribeEvent
	public void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event)
	{ this.serverApi.serverPlayerJoinEvent(getServerPlayerWrapper(event)); }
	@SubscribeEvent
	public void playerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event)
	{ this.serverApi.serverPlayerDisconnectEvent(getServerPlayerWrapper(event)); }
	@SubscribeEvent
	public void playerChangedDimensionEvent(PlayerEvent.PlayerChangedDimensionEvent event)
	{
		this.serverApi.serverPlayerLevelChangeEvent(
				getServerPlayerWrapper(event),
				getServerLevelWrapper(event.fromDim, event),
				getServerLevelWrapper(event.toDim, event)
		);
	}

    @SubscribeEvent
    public void clickBlockEvent(PlayerInteractEvent event)
    {
        if (SharedApi.isChunkAtBlockPosAlreadyUpdating(event.x, event.z))
        {
            return;
        }

        //LOGGER.trace("interact or block place event at blockPos: " + event.getPos());

        World level = event.world;

        AbstractExecutorService executor = ThreadPoolUtil.getFileHandlerExecutor();
        if (executor != null)
        {
            executor.execute(() ->
            {
                Chunk chunk = level.getChunkFromBlockCoords(event.x, event.z);
                ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(level);
                SharedApi.INSTANCE.chunkBlockChangedEvent(new ChunkWrapper(chunk, wrappedLevel), wrappedLevel);
            });
        }
    }

    private static final Queue<ScheduledTask<?>> taskQueue = new ConcurrentLinkedQueue<>();

    // Schedule a task that runs on the main thread and returns a CompletableFuture result
    public static <T> CompletableFuture<T> schedule(boolean limited, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        taskQueue.add(new ScheduledTask<>(task, future, limited));
        return future;
    }

    private static class ScheduledTask<T> {
        private final Supplier<T> task;
        private final CompletableFuture<T> future;
        private final boolean limited;

        public ScheduledTask(Supplier<T> task, CompletableFuture<T> future, boolean limited) {
            this.task = task;
            this.future = future;
            this.limited = limited;
        }

        public void run() {
            try {
                future.complete(task.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }

        public boolean isLimited() {
            return limited;
        }
    }


	//================//
	// helper methods //
	//================//

	private static IServerLevelWrapper getServerLevelWrapper(WorldServer level) { return ServerLevelWrapper.getWrapper(level); }


	private static IServerLevelWrapper getServerLevelWrapper(int dim, PlayerEvent event)
	{
        WorldServer world = (WorldServer) event.player.worldObj;
        WorldServer worldDim = world.func_73046_m().worldServerForDimension(dim);
		return getServerLevelWrapper(worldDim);
	}

	private static IServerPlayerWrapper getServerPlayerWrapper(PlayerEvent event) {
		return ServerPlayerWrapper.getWrapper(
            (EntityPlayerMP) event.player
		);
	}

}
