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

package com.seibel.distanthorizons.core.config.gui;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * The base for all screens
 *
 * @author coolGi
 */
public abstract class AbstractScreen
{
	public long minecraftWindow;
	public int width;
	public int height;
	public int scaledWidth;
	public int scaledHeight;
	public int mouseX = 0;
	public int mouseY = 0;
	/** Weather it should close when you press the escape key */
	public boolean shouldCloseOnEsc = true;
	/** If set to true, the screen would close on the next tick (warning, it closes after tick gets called for a last time) */
	public boolean close = false;
	
	
	/** Called once when the screen is opened */
	public abstract void init();
	/** Called every frame */
	public abstract void render(float delta);
	public void tick() { }
	
	/** Called every time the window gets re-sized */
	public void onResize() { }

    /** What happens when the user closes the screen */
	public void onClose() { }
	
	// ---------- Random stuff that might be needed later on ---------- //
	/** File dropped into the screen */
	public void onFilesDrop(List<Path> files) { }
	
}
