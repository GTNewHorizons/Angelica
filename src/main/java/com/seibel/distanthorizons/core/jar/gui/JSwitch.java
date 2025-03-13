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

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.ConstructorProperties;
import java.io.IOException;
import java.io.ObjectOutputStream;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;

/**
 * A switch button cus Java dosnt have one
 *
 * <p>
 * Ever wanted a switch like
 * <code><a href="https://www.overflowarchives.com/wp-content/uploads/2020/05/overflow-archives-unity-3d-switch-button-ui.jpg">this</a></code>
 * or
 * <code><a href="https://c8.alamy.com/comp/2E3PHHW/day-night-mode-switch-ui-button-light-dark-mode-slider-theme-2E3PHHW.jpg">this</a></code>?
 * Well now you can this this class!
 * </p>
 *
 * Based off Java's JButton
 *
 * @author coolGi
 */
// TODO: Make this for the theme (and finish the documentation once it is done)
@SuppressWarnings("serial")
public class JSwitch extends AbstractButton
{
	private static final String uiClassID = "SwitchUI";
	
	/** Creates a switch with no set text or icons */
	public JSwitch()
	{
		this(null, null, null, null);
	}
	
	/**
	 * Creates a switch with an icon
	 *
	 * @param offIcon The deactivated icon image
	 * @param onIcon The activated icon image
	 */
	public JSwitch(Icon offIcon, Icon onIcon)
	{
		this(null, null, offIcon, onIcon);
	}
	
	/**
	 * Creates a switch with text
	 *
	 * @param offText the deactivated text of the button
	 * @param onText the activated text of the button
	 */
	@ConstructorProperties({"text"})
	public JSwitch(String offText, String onText)
	{
		this(offText, onText, null, null);
	}
	
	/**
	 * Creates a switch where properties are taken from the
	 * <code>Action</code> supplied.
	 *
	 * @param a the <code>Action</code> used to specify the code that runs when pressing
	 */
	public JSwitch(Action a)
	{
		this();
		setAction(a);
	}
	
	/**
	 * Creates a switch with initial text and an icon
	 *
	 * @param offText the deactivated text of the button
	 * @param onText the activated text of the button
	 * @param offIcon The deactivated icon image
	 * @param onIcon The activated icon image
	 */
	public JSwitch(String offText, String onText, Icon offIcon, Icon onIcon)
	{
		// Create the model
		setModel(new DefaultButtonModel());

//        this.trueLabel = trueLabel;
//        this.falseLabel = falseLabel;
//        double trueLenth = getFontMetrics( getFont() ).getStringBounds( trueLabel, getGraphics() ).getWidth();
//        double falseLenght = getFontMetrics( getFont() ).getStringBounds( falseLabel, getGraphics() ).getWidth();
//        max = (int)Math.max( trueLenth, falseLenght );
//        gap =  Math.max( 5, 5+(int)Math.abs(trueLenth - falseLenght ) );
//        thumbBounds  = new Dimension(max+gap*2,20);
//        globalWitdh =  max + thumbBounds.width+gap*2;
//        setModel( new DefaultButtonModel() );
//        setSelected( false );
//        addMouseListener( new MouseAdapter() {
//            @Override
//            public void mouseReleased( MouseEvent e ) {
//                if(new Rectangle( getPreferredSize() ).contains( e.getPoint() )) {
//                    setSelected( !isSelected() );
//                }
//            }
//        });
	}
	
	/**
	 * Resets the UI property to a value from the current look and feel
	 *
	 * @see JComponent#updateUI
	 */
	public void updateUI()
	{
		setUI((ButtonUI) UIManager.getUI(this));
	}
	
	
	@Override
	public void setSelected(boolean b)
	{
//        if(b){
//            setText( trueLabel );
//            setBackground( green );
//        } else {
//            setBackground( red );
//            setText( falseLabel );
//        }
		super.setSelected(b);
	}
	
	
	/**
	 * Returns a string that specifies the name of the L&amp;F class
	 * that renders this component.
	 *
	 * @return the string "ButtonUI"
	 * @see JComponent#getUIClassID
	 * @see UIDefaults#getUI
	 */
	public String getUIClassID()
	{
		return uiClassID;
	}
	
	
	/**
	 * Overrides <code>JComponent.removeNotify</code> to check if
	 * this button is currently set as the default button on the
	 * <code>RootPane</code>, and if so, sets the <code>RootPane</code>'s
	 * default button to <code>null</code> to ensure the
	 * <code>RootPane</code> doesn't hold onto an invalid button reference.
	 */
	public void removeNotify()
	{
		JRootPane root = SwingUtilities.getRootPane(this);
		super.removeNotify();
	}
	
}
