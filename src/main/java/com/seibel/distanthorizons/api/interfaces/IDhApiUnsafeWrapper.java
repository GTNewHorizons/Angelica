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

package com.seibel.distanthorizons.api.interfaces;

/**
 * Implemented by wrappers so developers can
 * access the underlying Minecraft object(s).
 *
 * @author James Seibel
 * @version 2023-6-17
 * @since API 1.0.0
 */
public interface IDhApiUnsafeWrapper
{
	
	/**
	 * Returns the Minecraft object this wrapper contains. <br>
	 * <strong>Warning</strong>: This object will be Minecraft
	 * version dependent and may change without notice. <br> <br>
	 *
	 * In order to cast this object to something usable, you may want
	 * to use <code>obj.getClass()</code> when in your IDE
	 * in order to determine what object this method returns for
	 * the specific version of Minecraft you are developing for.
	 */
	Object getWrappedMcObject();
	
}
