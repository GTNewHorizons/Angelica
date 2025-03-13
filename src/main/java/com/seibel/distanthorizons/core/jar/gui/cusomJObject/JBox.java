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

package com.seibel.distanthorizons.core.jar.gui.cusomJObject;

import javax.swing.*;
import java.awt.*;

/**
 * A rectangular box that can be placed with java swing
 *
 * @author coolGi
 */
public class JBox extends JComponent
{
	private static final String uiClassID = "BoxBarUI";
	
	private Color color;
	
	private int x;
	private int y;
	private int width;
	private int height;
	
	public JBox()
	{
		this(null);
	}
	
	public JBox(Color color)
	{
		this.color = color;
	}
	
	public JBox(Color color, Rectangle rectangle)
	{
		this(color, rectangle.x, rectangle.y, rectangle.width, rectangle.height);
	}
	
	public JBox(Color color, int x, int y, int width, int height)
	{
		this.color = color;
		setBounds(x, y, width, height);
	}
	
	public void setColor(Color color)
	{
		this.color = color;
	}
	
	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		g.setColor(color);
		g.fillRect(0, 0, getBounds().width, getBounds().height);
	}
	
}
