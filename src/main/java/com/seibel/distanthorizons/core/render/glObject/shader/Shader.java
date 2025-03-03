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

package com.seibel.distanthorizons.core.render.glObject.shader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import org.lwjgl.opengl.GL32;

/**
 * This object holds a OpenGL reference to a shader
 * and allows for reading in and compiling a shader file.
 *
 * @author James Seibel
 * @version 11-8-2021
 */
public class Shader
{
	/** OpenGL shader ID */
	public final int id;
	
	/**
	 * Creates a shader with specified type.
	 *
	 * @param type Either GL_VERTEX_SHADER or GL_FRAGMENT_SHADER.
	 * @param path File path of the shader
	 * @param absoluteFilePath If false the file path is relative to the resource jar folder.
	 * @throws RuntimeException if the shader fails to compile
	 */
	public Shader(int type, String path, boolean absoluteFilePath)
	{
		GLProxy.GL_LOGGER.info("Loading shader at " + path);
		// Create an empty shader object
		id = GL32.glCreateShader(type);
		StringBuilder source = loadFile(path, absoluteFilePath, new StringBuilder());
		GL32.glShaderSource(id, source);
		
		GL32.glCompileShader(id);
		// check if the shader compiled
		int status = GL32.glGetShaderi(id, GL32.GL_COMPILE_STATUS);
		if (status != GL32.GL_TRUE)
		{
			String message = "Shader compiler error. Details: " + GL32.glGetShaderInfoLog(id);
			free(); // important!
			throw new RuntimeException(message);
		}
		GLProxy.GL_LOGGER.info("Shader at " + path + " loaded sucessfully.");
	}
	
	public Shader(int type, String sourceString)
	{
		GLProxy.GL_LOGGER.info("Loading shader with type: {}", type);
		GLProxy.GL_LOGGER.debug("Source:\n{}", sourceString);
		// Create an empty shader object
		id = GL32.glCreateShader(type);
		GL32.glShaderSource(id, sourceString);
		
		GL32.glCompileShader(id);
		// check if the shader compiled
		int status = GL32.glGetShaderi(id, GL32.GL_COMPILE_STATUS);
		if (status != GL32.GL_TRUE)
		{
			
			String message = "Shader compiler error. Details: " + GL32.glGetShaderInfoLog(id);
			message += "\nSource:\n" + sourceString;
			free(); // important!
			throw new RuntimeException(message);
		}
		GLProxy.GL_LOGGER.info("Shader loaded sucessfully.");
	}
	
	// REMEMBER to always free the resource!
	public void free()
	{
		GL32.glDeleteShader(id);
	}
	
	public static StringBuilder loadFile(String path, boolean absoluteFilePath, StringBuilder stringBuilder)
	{
		try
		{
			// open the file
			InputStream in;
			if (absoluteFilePath)
			{
				// Throws FileNotFoundException
				in = new FileInputStream(path); // Note: this should use OS path seperator
			}
			else
			{
				in = Shader.class.getClassLoader().getResourceAsStream(path); // Note: path seperator should be '/'
				if (in == null)
				{
					throw new FileNotFoundException("Shader file not found in resource: " + path);
				}
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			
			// read in the file
			String line;
			while ((line = reader.readLine()) != null)
			{
				stringBuilder.append(line).append("\n");
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Unable to load shader from file [" + path + "]. Error: " + e.getMessage());
		}
		return stringBuilder;
	}
	
}
