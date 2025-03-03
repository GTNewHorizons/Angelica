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

package com.seibel.distanthorizons.core.util.objects;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public final class GLMessageOutputStream extends OutputStream
{
	final Consumer<GLMessage> func;
	final GLMessage.Builder builder;
	
	
	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	
	public GLMessageOutputStream(Consumer<GLMessage> func, GLMessage.Builder builder)
	{
		this.func = func;
		this.builder = builder;
	}
	
	@Override
	public void write(int b)
	{
		buffer.write(b);
		if (b == '\n') flush();
	}
	
	@Override
	public void flush()
	{
		String str = buffer.toString();
		GLMessage msg = builder.add(str);
		if (msg != null) func.accept(msg);
		buffer.reset();
	}
	
	@Override
	public void close() throws IOException
	{
		flush();
		buffer.close();
	}
	
}
