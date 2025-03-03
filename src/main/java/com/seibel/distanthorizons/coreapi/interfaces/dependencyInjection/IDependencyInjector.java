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

package com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection;


import java.util.ArrayList;

public interface IDependencyInjector<BindableType extends IBindable>
{
	
	/**
	 * Links the given implementation object to an interface, so it can be referenced later.
	 *
	 * @param dependencyInterface The interface (or parent class) the implementation object should implement.
	 * @param dependencyImplementation An object that implements the dependencyInterface interface.
	 * @throws IllegalStateException if the interface has already been bound and duplicates aren't allowed
	 * @throws IllegalArgumentException if the implementation object doesn't implement the interface
	 */
	void bind(Class<? extends BindableType> dependencyInterface, BindableType dependencyImplementation) throws IllegalStateException, IllegalArgumentException;
	/**
	 * Checks if classToTest (or one of its ancestors)
	 * implements the given interface.
	 */
	boolean checkIfClassImplements(Class<?> classToTest, Class<?> interfaceToLookFor);
	/** Checks if classToTest extends the given class. */
	boolean checkIfClassExtends(Class<?> classToTest, Class<?> extensionToLookFor);
	
	
	/**
	 * Does not return incomplete dependencies. <Br>
	 * See {@link #get(Class, boolean) get(Class, boolean)} for full documentation.
	 *
	 * @see #get(Class, boolean)
	 */
	@SuppressWarnings("unchecked") <T extends BindableType> T get(Class<T> interfaceClass) throws ClassCastException;
	
	/**
	 * Returns all dependencies of type T that have been bound. <br>
	 * Returns an empty list if no dependencies have been bound.
	 *
	 * @param <T> class of the dependency
	 * (inferred from the objectClass parameter)
	 * @param interfaceClass Interface of the dependency
	 * @return the dependency of type T
	 * @throws ClassCastException If the dependency isn't able to be cast to type T.
	 * (this shouldn't normally happen, unless the bound object changed somehow)
	 */
	<T extends BindableType> ArrayList<T> getAll(Class<T> interfaceClass) throws ClassCastException;
	
	/**
	 * Returns a dependency of type T if one has been bound. <br>
	 * Returns null if a dependency hasn't been bound. <br> <br>
	 *
	 * If the handler allows duplicate bindings, this will return the first bound dependency.
	 *
	 * @param <T> class of the dependency
	 * (inferred from the interfaceClass parameter)
	 * @param interfaceClass Interface of the dependency
	 * @param allowIncompleteDependencies If true this method will also return dependencies that haven't completed their delayed setup.
	 * @return the dependency of type T
	 * @throws ClassCastException If the dependency isn't able to be cast to type T.
	 * (this shouldn't normally happen, unless the bound object changed somehow)
	 */
	@SuppressWarnings("unchecked") <T extends BindableType> T get(Class<T> interfaceClass, boolean allowIncompleteDependencies) throws ClassCastException;
	
	
	
	/** Removes all bound dependencies. */
	void clear();
	
	
	
	/** Runs delayed setup for any dependencies that require it. */
	void runDelayedSetup();
	
}
