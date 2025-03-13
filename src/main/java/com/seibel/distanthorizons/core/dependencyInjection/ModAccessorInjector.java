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

package com.seibel.distanthorizons.core.dependencyInjection;

import com.seibel.distanthorizons.coreapi.DependencyInjection.DependencyInjector;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;

/**
 * This class takes care of dependency injection for mod accessors. (for mod compatibility
 * support).  <Br> <Br>
 *
 * If a IModAccessor returns null either that means the mod isn't loaded in the game
 * or an Accessor hasn't been implemented for the given Minecraft version.
 *
 * @author James Seibel
 * @author Leetom
 * @version 2022-7-18
 */
public class ModAccessorInjector extends DependencyInjector<IModAccessor>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	
	public static final ModAccessorInjector INSTANCE = new ModAccessorInjector(IModAccessor.class);
	
	
	public ModAccessorInjector(Class<IModAccessor> newBindableInterface)
	{
		super(newBindableInterface, false);
	}
	
	
	/**
	 * Go to {@link DependencyInjector#bind(Class, IBindable)} DependencyHandler.bind()}
	 * for this method's javadocs.
	 */
	@Override
	public void bind(Class<? extends IModAccessor> interfaceClass, IModAccessor modAccessor)
			throws IllegalStateException, IllegalArgumentException
	{
		super.bind(interfaceClass, modAccessor);
		LOGGER.info("Registered mod compatibility accessor for: [" + modAccessor.getModName() + "].");
	}
	
	
}
