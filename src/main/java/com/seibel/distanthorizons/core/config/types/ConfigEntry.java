/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.config.types;


import com.seibel.distanthorizons.core.config.NumberUtil;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;
import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryAppearance;
import com.seibel.distanthorizons.core.config.types.enums.EConfigEntryPerformance;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConfigEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Use for making the config variables
 * for types that are not supported by it look in ConfigBase
 *
 * @author coolGi
 * @version 2023-7-16
 */
public class ConfigEntry<T> extends AbstractConfigType<T, ConfigEntry<T>> implements IConfigEntry<T>
{
	private String comment;
	private T min;
	private T max;
	private final ArrayList<IConfigListener> listenerList;
	private final String chatCommandName;
	
	private final EConfigEntryPerformance performance;
	
	// API control //
	/**
	 * If true this config can be controlled by the API <br>
	 * and any get() method calls will return the apiValue if it is set.
	 */
	public final boolean allowApiOverride;
	private T apiValue;
	
	
	
	/** Creates the entry */
	private ConfigEntry(
			EConfigEntryAppearance appearance, 
			T value, String comment, T min, T max, 
			String chatCommandName, boolean allowApiOverride, 
			EConfigEntryPerformance performance, 
			ArrayList<IConfigListener> listenerList)
	{
		super(appearance, value);
		
		this.comment = comment;
		this.min = min;
		this.max = max;
		this.chatCommandName = chatCommandName;
		this.allowApiOverride = allowApiOverride;
		this.performance = performance;
		this.listenerList = listenerList;
	}
	
	
	
	/** Gets the default value of the option */
	@Override
	public T getDefaultValue() { return super.defaultValue; }
	
	@Override
	public void setApiValue(T newApiValue)
	{
		this.apiValue = newApiValue;
		this.listenerList.forEach(IConfigListener::onConfigValueSet);
	}
	@Override
	public T getApiValue() { return this.apiValue; }
	@Override
	public boolean getAllowApiOverride() { return this.allowApiOverride; }
	
	/** 
	 * DONT USE THIS IN YOUR CODE <br>
	 * Sets the value without informing the rest of the code (ie, doesnt call listeners, or saves the value). <br>
	 * Should only be used when loading the config from the file (in places like the {@link com.seibel.distanthorizons.core.config.file.ConfigFileHandling} or {@link com.seibel.distanthorizons.core.config.ConfigBase})
	 */
	public void pureSet(T newValue) {
		super.set(newValue);
	}
	
	/** Sets the value without saving */
	@Override
	public void setWithoutSaving(T newValue)
	{
		super.set(newValue);
		this.listenerList.forEach(IConfigListener::onConfigValueSet);
	}
	@Override
	public void set(T newValue)
	{
		this.setWithoutSaving(newValue);
		this.save();
	}
	
	public void uiSetWithoutSaving(T newValue)
	{
		this.setWithoutSaving(newValue);
		this.listenerList.forEach(IConfigListener::onUiModify);
	}
	public void uiSet(T newValue)
	{
		this.set(newValue);
		this.listenerList.forEach(IConfigListener::onUiModify);
	}
	
	
	@Override
	public T get()
	{
		if (allowApiOverride && apiValue != null)
		{
			return apiValue;
		}
		
		return super.get();
	}
	@Override
	public T getTrueValue()
	{
		return super.get();
	}
	
	
	/** Gets the min value */
	@Override
	public T getMin() { return this.min; }
	/** Sets the min value */
	@Override
	public void setMin(T newMin) { this.min = newMin; }
	/** Gets the max value */
	@Override
	public T getMax() { return this.max; }
	/** Sets the max value */
	@Override
	public void setMax(T newMax) { this.max = newMax; }
	/** Sets the min and max within a single setter */
	@Override
	public void setMinMax(T newMin, T newMax)
	{
		this.setMin(newMin);
		this.setMax(newMax);
	}
	
	/**
	 * Clamps the value within the set range
	 *
	 * @apiNote This does not save the value
	 */
	public void clampWithinRange() { this.clampWithinRange(this.min, this.max); }
	/**
	 * Clamps the value within a set range
	 *
	 * @param min The minimum that the value can be
	 * @param max The maximum that the value can be
	 * @apiNote This does not save the value
	 */
	@SuppressWarnings("unchecked") // Suppress due to its always safe
	public void clampWithinRange(T min, T max)
	{
		byte validness = this.isValid(min, max);
		if (validness == -1) this.value = (T) NumberUtil.getMinimum(this.value.getClass());
		if (validness == 1) this.value = (T) NumberUtil.getMaximum(this.value.getClass());
	}
	
	// TODO is this for command line use?
	public String getChatCommandName() { return this.chatCommandName; }
	
	@Override
	public String getComment() { return this.comment; }
	@Override
	public void setComment(String newComment) { this.comment = newComment; }
	
	/** Gets the performance impact of an option */
	public EConfigEntryPerformance getPerformance() { return this.performance; }
	
	/** Fired whenever the config value changes to a new value. */
	public void addValueChangeListener(Consumer<T> onValueChangeFunc)
	{
		ConfigChangeListener<T> changeListener = new ConfigChangeListener<>(this, onValueChangeFunc);
		this.addListener(changeListener);
	}
	/** Fired whenever the config value is updated, including when the value doesn't change (IE when the UI changes state or the config is reloaded). */
	public void addListener(IConfigListener newListener) { this.listenerList.add(newListener); }
	
	//public void removeValueChangeListener(Consumer<T> onValueChangeFunc) { } // not currently implemented
	public void removeListener(IConfigListener oldListener) { this.listenerList.remove(oldListener); }
	
	public void clearListeners() { this.listenerList.clear(); }
	public ArrayList<IConfigListener> getListeners() { return this.listenerList; }
	/** Replaces the listener list */
	public void setListeners(ArrayList<IConfigListener> newListeners)
	{
		this.listenerList.clear();
		this.listenerList.addAll(newListeners);
	}
	public void setListeners(IConfigListener... newListeners) { this.listenerList.addAll(Arrays.asList(newListeners)); }
	
	
	/**
	 * Checks if the option is valid
	 *
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	@Override
	public byte isValid() { return isValid(this.value, this.min, this.max); }
	/**
	 * Checks if a new value is valid
	 *
	 * @param value Value that is being checked whether valid
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	@Override
	public byte isValid(T value) { return this.isValid(value, this.min, this.max); }
	/**
	 * Checks if a new value is valid
	 *
	 * @param min The minimum that the value can be
	 * @param max The maximum that the value can be
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	public byte isValid(T min, T max) { return this.isValid(this.value, min, max); }
	/**
	 * Checks if a new value is valid
	 *
	 * @param value Value that is being checked whether valid
	 * @param min The minimum that the value can be
	 * @param max The maximum that the value can be
	 * @return 0 == valid
	 * <p>  2 == invalid
	 * <p>  1 == number too high
	 * <p> -1 == number too low
	 */
	public byte isValid(T value, T min, T max)
	{
		if (this.configBase.disableMinMax)
		{
			return 0;
		}
		else if (min == null && max == null)
		{
			// no validation is needed for this field
			return 0;
		}
		else if (value == null || this.value == null
				|| value.getClass() != this.value.getClass())
		{
			// If the 2 variables aren't the same type then it will be invalid
			return 2;
		}
		else if (Number.class.isAssignableFrom(value.getClass()))
		{ 
			// Only check min max if it is a number
			if (max != null && NumberUtil.greaterThan((Number) value, (Number) max))
			{
				return 1;
			}
			if (min != null && NumberUtil.lessThan((Number) value, (Number) min))
			{
				return -1;
			}
			
			return 0;
		}
		else
		{
			return 0;
		}
	}
	
	/** This should normally not be called since set() automatically calls this */
	public void save() { configBase.configFileINSTANCE.saveEntry(this); }
	/** This should normally not be called except for special circumstances */
	public void load() { configBase.configFileINSTANCE.loadEntry(this); }
	
	
	@Override
	public boolean equals(IConfigEntry<?> obj) { return obj.getClass() == ConfigEntry.class && equals((ConfigEntry<?>) obj); }
	/** Is the value of this equal to another */
	public boolean equals(ConfigEntry<?> obj)
	{
		// Can all of this just be "return this.value.equals(obj.value)"?
		
		if (Number.class.isAssignableFrom(this.value.getClass()))
			return this.value == obj.value;
		else
			return this.value.equals(obj.value);
	}
	
	
	public static class Builder<T> extends AbstractConfigType.Builder<T, Builder<T>>
	{
		private String tmpComment = null;
		private T tmpMin = null;
		private T tmpMax = null;
		protected String tmpChatCommandName = null;
		private boolean tmpUseApiOverwrite = true;
		private EConfigEntryPerformance tmpPerformance = EConfigEntryPerformance.DONT_SHOW;
		protected ArrayList<IConfigListener> tmpIConfigListener = new ArrayList<>();
		
		public Builder<T> comment(String newComment)
		{
			this.tmpComment = newComment;
			return this;
		}
		
		/** Allows most values to be set by 1 setter */
		public Builder<T> setMinDefaultMax(T newMin, T newDefault, T newMax)
		{
			this.set(newDefault);
			this.setMinMax(newMin, newMax);
			return this;
		}
		
		public Builder<T> setMinMax(T newMin, T newMax)
		{
			this.tmpMin = newMin;
			this.tmpMax = newMax;
			return this;
		}
		
		public Builder<T> setMin(T newMin)
		{
			this.tmpMin = newMin;
			return this;
		}
		
		public Builder<T> setMax(T newMax)
		{
			this.tmpMax = newMax;
			return this;
		}
		
		public Builder<T> setChatCommandName(String name)
		{
			this.tmpChatCommandName = name;
			return this;
		}
		
		public Builder<T> setUseApiOverwrite(boolean newUseApiOverwrite)
		{
			this.tmpUseApiOverwrite = newUseApiOverwrite;
			return this;
		}
		
		public Builder<T> setPerformance(EConfigEntryPerformance newPerformance)
		{
			this.tmpPerformance = newPerformance;
			return this;
		}
		
		
		
		public Builder<T> replaceListeners(ArrayList<IConfigListener> newConfigListener)
		{
			this.tmpIConfigListener = newConfigListener;
			return this;
		}
		
		public Builder<T> addListeners(IConfigListener... newConfigListener)
		{
			this.tmpIConfigListener.addAll(Arrays.asList(newConfigListener));
			return this;
		}
		
		public Builder<T> addListener(IConfigListener newConfigListener)
		{
			this.tmpIConfigListener.add(newConfigListener);
			return this;
		}
		
		public Builder<T> clearListeners()
		{
			this.tmpIConfigListener.clear();
			return this;
		}
		
		
		
		public ConfigEntry<T> build()
		{
			return new ConfigEntry<>(
					this.tmpAppearance, 
					this.tmpValue, this.tmpComment, this.tmpMin, this.tmpMax, 
					this.tmpChatCommandName, this.tmpUseApiOverwrite, 
					this.tmpPerformance, this.tmpIConfigListener);
		}
		
	}
	
}
