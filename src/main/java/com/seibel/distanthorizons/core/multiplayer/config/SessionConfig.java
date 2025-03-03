package com.seibel.distanthorizons.core.multiplayer.config;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.network.INetworkObject;
import io.netty.buffer.ByteBuf;

import java.io.Closeable;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SessionConfig implements INetworkObject
{
	private static final LinkedHashMap<String, Entry> CONFIG_ENTRIES = new LinkedHashMap<>();
	
	
	private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
	public SessionConfig constrainingConfig;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	static
	{
		// Note: config values are transmitted in the insertion order
		
		registerConfigEntry(Config.Common.WorldGenerator.enableDistantGeneration, Boolean::logicalAnd);
		registerConfigEntry(Config.Server.maxGenerationRequestDistance, Math::min);
		registerConfigEntry(Config.Server.generationBoundsX, (x, y) -> x);
		registerConfigEntry(Config.Server.generationBoundsZ, (x, y) -> x);
		registerConfigEntry(Config.Server.generationBoundsRadius, (x, y) -> x);
		registerConfigEntry(Config.Server.generationRequestRateLimit, Math::min);
		
		registerConfigEntry(Config.Server.enableRealTimeUpdates, Boolean::logicalAnd);
		registerConfigEntry(Config.Server.realTimeUpdateDistanceRadiusInChunks, Math::min);
		
		registerConfigEntry(Config.Server.synchronizeOnLoad, Boolean::logicalAnd);
		registerConfigEntry(Config.Server.maxSyncOnLoadRequestDistance, Math::min);
		registerConfigEntry(Config.Server.syncOnLoadRateLimit, Math::min);
		
		registerConfigEntry(Config.Server.maxDataTransferSpeed, (x, y) -> {
			if (x == 0 && y == 0)
			{
				return 0;
			}
			
			return Math.min(
					x > 0 ? x : Integer.MAX_VALUE,
					y > 0 ? y : Integer.MAX_VALUE
			);
		});
	}
	
	public SessionConfig() {}
	
	
	
	//===============//
	// public values //
	//===============//
	
	public boolean isDistantGenerationEnabled() { return this.getValue(Config.Common.WorldGenerator.enableDistantGeneration); }
	public int getMaxGenerationRequestDistance() { return this.getValue(Config.Server.maxGenerationRequestDistance); }
	public Integer getGenerationBoundsX() { return this.getValue(Config.Server.generationBoundsX); }
	public Integer getGenerationBoundsZ() { return this.getValue(Config.Server.generationBoundsZ); }
	public Integer getGenerationBoundsRadius() { return this.getValue(Config.Server.generationBoundsRadius); }
	public int getGenerationRequestRateLimit() { return this.getValue(Config.Server.generationRequestRateLimit); }
	
	public boolean isRealTimeUpdatesEnabled() { return this.getValue(Config.Server.enableRealTimeUpdates); }
	public int getMaxUpdateDistanceRadius() { return this.getValue(Config.Server.realTimeUpdateDistanceRadiusInChunks); }
	
	public boolean getSynchronizeOnLoad() { return this.getValue(Config.Server.synchronizeOnLoad); }
	public int getMaxSyncOnLoadDistance() { return this.getValue(Config.Server.maxSyncOnLoadRequestDistance); }
	public int getSyncOnLoginRateLimit() { return this.getValue(Config.Server.syncOnLoadRateLimit); }
	
	public int getMaxDataTransferSpeed() { return this.getValue(Config.Server.maxDataTransferSpeed); }
	
	
	
	//====================//
	// entry registration //
	//====================//
	
	private static <T> void registerConfigEntry(ConfigEntry<T> configEntry, BinaryOperator<T> valueConstrainer)
	{
		CONFIG_ENTRIES.compute(Objects.requireNonNull(configEntry.getChatCommandName()), (key, existingEntry) -> {
			if (existingEntry != null)
			{
				throw new IllegalArgumentException("Attempted to register config entry with duplicate chatCommandName: " + key);
			}
			
			return new Entry(configEntry, valueConstrainer);
		});
	}
	
	
	
	//==================//
	// internal getters //
	//==================//
	
	private <T> T getValue(ConfigEntry<T> configEntry) { return this.getValue(configEntry.getChatCommandName()); }
	@SuppressWarnings("unchecked")
	private <T> T getValue(String name)
	{
		Entry entry = CONFIG_ENTRIES.get(name);
		
		T value = (T) this.values.get(name);
		if (value == null)
		{
			value = (T) entry.supplier.get();
		}
		
		return (this.constrainingConfig != null
				? (T) entry.valueConstrainer.apply(value, this.constrainingConfig.getValue(name))
				: value);
	}
	
	private Map<String, ?> getValues()
	{
		return CONFIG_ENTRIES.keySet().stream().collect(Collectors.toMap(
				Function.identity(),
				this::getValue,
				(x, y) -> x,
				LinkedHashMap::new
		));
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf outBuffer) { this.writeFixedLengthCollection(outBuffer, this.getValues().values()); }
	
	@Override
	public void decode(ByteBuf inBuffer)
	{
		for (String key : CONFIG_ENTRIES.keySet())
		{
			Object currentValue = this.getValue(key);
			Object newValue = Codec.getCodec(currentValue.getClass()).decode.apply(currentValue, inBuffer);
			this.values.put(key, newValue);
		}
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("values", this.getValues())
				.toString();
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class Entry
	{
		public final ConfigEntry<Object> supplier;
		public final BinaryOperator<Object> valueConstrainer;
		
		@SuppressWarnings("unchecked")
		private <T> Entry(ConfigEntry<T> supplier, BinaryOperator<T> valueConstrainer)
		{
			this.supplier = (ConfigEntry<Object>) supplier;
			this.valueConstrainer = (BinaryOperator<Object>) valueConstrainer;
		}
		
	}
	
	/** fires if any config value was changed */
	public static class AnyChangeListener implements Closeable
	{
		private final ArrayList<ConfigChangeListener<?>> changeListeners;
		
		public AnyChangeListener(Runnable runnable)
		{
			this.changeListeners = new ArrayList<>(CONFIG_ENTRIES.size());
			for (Entry entry : CONFIG_ENTRIES.values())
			{
				this.changeListeners.add(new ConfigChangeListener<>(entry.supplier, ignored -> runnable.run()));
			}
		}
		
		@Override
		public void close()
		{
			for (ConfigChangeListener<?> changeListener : this.changeListeners)
			{
				changeListener.close();
			}
			this.changeListeners.clear();
		}
		
	}
	
}