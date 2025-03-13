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

package com.seibel.distanthorizons.core.jar.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.AbstractButton;
import javax.swing.DefaultButtonModel;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * Taken from https://github.com/sshtools/ui/blob/master/src/main/java/com/sshtools/ui/swing/JSwitchBox.java
 *
 * @author sshtools
 */
// TODO: Merge this with my own JSwitch rather than use all their code
// TODO: Make it work with the theme rather than do whatever it is doing now
public class JSwitchBox extends AbstractButton
{
	private Color shadow1 = UIManager.getColor("controlHighlight");
	private Color shadow2 = UIManager.getColor("control");
	private Color colorBright = UIManager.getColor("Button.light");
	private Color red = UIManager.getColor("controlShadow");
	private Color redf = UIManager.getColor("Button.foreground");
	private Color trackBackground = UIManager.getColor("textHighlight");
	private Color trackBackgroundText = UIManager.getColor("textHighlightText");
	private Border buttonBorder = UIManager.getBorder("Button.border");
	private Border trackBorder = UIManager.getBorder("Button.border");
	
	private Font font = new JLabel().getFont();
	private int gap = 5;
	private int globalWitdh = 0;
	private final String trueLabel;
	private final String falseLabel;
	private Dimension thumbBounds;
	private Rectangle2D bounds;
	private int max;
	
	public JSwitchBox(String trueLabel, String falseLabel)
	{
		setBackground(UIManager.getColor("Panel.background"));
		this.trueLabel = trueLabel;
		this.falseLabel = falseLabel;
		FontMetrics fontMetrics = getFontMetrics(getFont());
		double trueLenth = fontMetrics
				.getStringBounds(trueLabel, getGraphics()).getWidth();
		double falseLenght = fontMetrics.getStringBounds(falseLabel,
				getGraphics()).getWidth();
		max = (int) Math.max(trueLenth, falseLenght);
		gap = Math.max(5, 5 + (int) Math.abs(trueLenth - falseLenght));
		thumbBounds = new Dimension(max + gap * 2,
				(int) ((float) fontMetrics.getHeight() * 1.5));
		globalWitdh = max + thumbBounds.width + gap * 2;
		setModel(new DefaultButtonModel());
		setSelected(false);
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (new Rectangle(getPreferredSize()).contains(e.getPoint()))
				{
					setSelected(!isSelected());
				}
			}
		});
	}
	
	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(globalWitdh, thumbBounds.height);
	}
	
	@Override
	public void setSelected(boolean b)
	{
		if (b)
		{
			setText(trueLabel);
			setBackground(trackBackground);
			setForeground(trackBackgroundText);
		}
		else
		{
			setBackground(red);
			setForeground(redf);
			setText(falseLabel);
		}
		super.setSelected(b);
	}
	
	@Override
	public void setText(String text)
	{
		super.setText(text);
	}
	
	@Override
	public int getHeight()
	{
		
		return getPreferredSize().height;
	}
	
	@Override
	public int getWidth()
	{
		return getPreferredSize().width;
	}
	
	@Override
	public Font getFont()
	{
		return font;
	}
	
	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight() - 4);
		Graphics2D g2 = (Graphics2D) g;
		
		// g2.setColor(black);
		// g2.drawRoundRect(1, 1, getWidth() - 2 - 1, getHeight() - 2 - 1, 2,
		// 2);
		// g2.setColor(white);
		// g2.drawRoundRect(1 + 1, 1 + 1, getWidth() - 2 - 3, getHeight() - 2 -
		// 3,
		// 2, 2);
		
		trackBorder.paintBorder(this, g2, 0, 2, getWidth(), getHeight() - 4);
		
		int buttonX = 0;
		int textX = 0;
		if (isSelected())
		{
			textX = thumbBounds.width;
		}
		else
		{
			buttonX = thumbBounds.width;
		}
		int y = 0;
		int w = thumbBounds.width;
		int h = thumbBounds.height;
		
		g2.setPaint(new GradientPaint(buttonX, (int) (y - 0.1 * h), shadow2,
				buttonX, (int) (y + 1.2 * h), shadow1));
		g2.fillRect(buttonX, y, w, h);
		g2.setPaint(new GradientPaint(buttonX, (int) (y + .65 * h), shadow1,
				buttonX, (int) (y + 1.3 * h), shadow2));
		g2.fillRect(buttonX, (int) (y + .65 * h), w, (int) (h - .65 * h));
		
		if (w > 14)
		{
			int size = 10;
			g2.setColor(colorBright);
			g2.fillRect(buttonX + w / 2 - size / 2, y + h / 2 - size / 2, size,
					size);
			g2.setColor(colorBright.darker());
			g2.fillRect(buttonX + w / 2 - 4, h / 2 - 4, 2, 2);
			g2.fillRect(buttonX + w / 2 - 1, h / 2 - 4, 2, 2);
			g2.fillRect(buttonX + w / 2 + 2, h / 2 - 4, 2, 2);
			g2.setColor(colorBright.darker().darker());
			g2.fillRect(buttonX + w / 2 - 4, h / 2 - 2, 2, 6);
			g2.fillRect(buttonX + w / 2 - 1, h / 2 - 2, 2, 6);
			g2.fillRect(buttonX + w / 2 + 2, h / 2 - 2, 2, 6);
			g2.setColor(colorBright.darker());
			g2.fillRect(buttonX + w / 2 - 4, h / 2 + 2, 2, 2);
			g2.fillRect(buttonX + w / 2 - 1, h / 2 + 2, 2, 2);
			g2.fillRect(buttonX + w / 2 + 2, h / 2 + 2, 2, 2);
		}
		
		buttonBorder.paintBorder(this, g2, buttonX, y, w, h);
		// g2.setColor(black);
		// g2.drawRoundRect(x, y, w - 1, h - 1, 2, 2);
		// g2.setColor(white);
		// g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 2, 2);
		
		g2.setColor(getForeground());
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setFont(getFont());
		g2.drawString(getText(), textX + gap, y + h / 2 + h / 4);
	}
	
}