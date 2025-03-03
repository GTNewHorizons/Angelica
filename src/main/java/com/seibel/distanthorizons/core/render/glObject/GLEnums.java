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

package com.seibel.distanthorizons.core.render.glObject;

import static org.lwjgl.opengl.GL46.*;

// Turns GL int enums back to readable strings
public class GLEnums
{
	
	public static String getString(int glEnum)
	{
		// blend stuff
		switch (glEnum)
		{
			case GL_ZERO:
				return "GL_ZERO";
			case GL_ONE:
				return "GL_ONE";
			case GL_SRC_COLOR:
				return "GL_SRC_COLOR";
			case GL_ONE_MINUS_SRC_COLOR:
				return "GL_ONE_MINUS_SRC_COLOR";
			case GL_DST_COLOR:
				return "GL_DST_COLOR";
			case GL_ONE_MINUS_DST_COLOR:
				return "GL_ONE_MINUS_DST_COLOR";
			case GL_SRC_ALPHA:
				return "GL_SRC_ALPHA";
			case GL_ONE_MINUS_SRC_ALPHA:
				return "GL_ONE_MINUS_SRC_ALPHA";
			case GL_DST_ALPHA:
				return "GL_DST_ALPHA";
			case GL_ONE_MINUS_DST_ALPHA:
				return "GL_ONE_MINUS_DST_ALPHA";
			case GL_CONSTANT_COLOR:
				return "GL_CONSTANT_COLOR";
			case GL_ONE_MINUS_CONSTANT_COLOR:
				return "GL_ONE_MINUS_CONSTANT_COLOR";
			case GL_CONSTANT_ALPHA:
				return "GL_CONSTANT_ALPHA";
			case GL_ONE_MINUS_CONSTANT_ALPHA:
				return "GL_ONE_MINUS_CONSTANT_ALPHA";
			default:
		}
		
		// shader stuff
		switch (glEnum)
		{
			case GL_VERTEX_SHADER:
				return "GL_VERTEX_SHADER";
			case GL_GEOMETRY_SHADER:
				return "GL_GEOMETRY_SHADER";
			case GL_FRAGMENT_SHADER:
				return "GL_FRAGMENT_SHADER";
			default:
		}
		
		// stencil stuff
		switch (glEnum)
		{
			case GL_KEEP:
				return "GL_KEEP";
			case GL_ZERO:
				return "GL_ZERO";
			case GL_REPLACE:
				return "GL_REPLACE";
			case GL_INCR:
				return "GL_INCR";
			case GL_DECR:
				return "GL_DECR";
			case GL_INVERT:
				return "GL_INVERT";
			case GL_INCR_WRAP:
				return "GL_INCR_WRAP";
			case GL_DECR_WRAP:
				return "GL_DECR_WRAP";
			default:
		}
		
		// depth stuff
		switch (glEnum)
		{
			case GL_NEVER:
				return "GL_NEVER";
			case GL_LESS:
				return "GL_LESS";
			case GL_EQUAL:
				return "GL_EQUAL";
			case GL_LEQUAL:
				return "GL_LEQUAL";
			case GL_GREATER:
				return "GL_GREATER";
			case GL_NOTEQUAL:
				return "GL_NOTEQUAL";
			case GL_GEQUAL:
				return "GL_GEQUAL";
			case GL_ALWAYS:
				return "GL_ALWAYS";
			default:
		}
		
		// Texture binding points
		switch (glEnum)
		{
			case GL_TEXTURE0:
				return "GL_TEXTURE0";
			case GL_TEXTURE1:
				return "GL_TEXTURE1";
			case GL_TEXTURE2:
				return "GL_TEXTURE2";
			case GL_TEXTURE3:
				return "GL_TEXTURE3";
			case GL_TEXTURE4:
				return "GL_TEXTURE4";
			case GL_TEXTURE5:
				return "GL_TEXTURE5";
			case GL_TEXTURE6:
				return "GL_TEXTURE6";
			case GL_TEXTURE7:
				return "GL_TEXTURE7";
			case GL_TEXTURE8:
				return "GL_TEXTURE8";
			case GL_TEXTURE9:
				return "GL_TEXTURE9";
			case GL_TEXTURE10:
				return "GL_TEXTURE10";
			case GL_TEXTURE11:
				return "GL_TEXTURE11";
			case GL_TEXTURE12:
				return "GL_TEXTURE12";
			case GL_TEXTURE13:
				return "GL_TEXTURE13";
			case GL_TEXTURE14:
				return "GL_TEXTURE14";
			case GL_TEXTURE15:
				return "GL_TEXTURE15";
			case GL_TEXTURE16:
				return "GL_TEXTURE16";
			case GL_TEXTURE17:
				return "GL_TEXTURE17";
			case GL_TEXTURE18:
				return "GL_TEXTURE18";
			case GL_TEXTURE19:
				return "GL_TEXTURE19";
			case GL_TEXTURE20:
				return "GL_TEXTURE20";
			case GL_TEXTURE21:
				return "GL_TEXTURE21";
			case GL_TEXTURE22:
				return "GL_TEXTURE22";
			case GL_TEXTURE23:
				return "GL_TEXTURE23";
			case GL_TEXTURE24:
				return "GL_TEXTURE24";
			case GL_TEXTURE25:
				return "GL_TEXTURE25";
			case GL_TEXTURE26:
				return "GL_TEXTURE26";
			case GL_TEXTURE27:
				return "GL_TEXTURE27";
			case GL_TEXTURE28:
				return "GL_TEXTURE28";
			case GL_TEXTURE29:
				return "GL_TEXTURE29";
			case GL_TEXTURE30:
				return "GL_TEXTURE30";
			case GL_TEXTURE31:
				return "GL_TEXTURE31";
			default:
		}
		
		// Polygon modes
		switch (glEnum)
		{
			case GL_POINT:
				return "GL_POINT";
			case GL_LINE:
				return "GL_LINE";
			case GL_FILL:
				return "GL_FILL";
			default:
		}
		
		// Culling modes
		switch (glEnum)
		{
			case GL_FRONT:
				return "GL_FRONT";
			case GL_BACK:
				return "GL_BACK";
			case GL_FRONT_AND_BACK:
				return "GL_FRONT_AND_BACK";
			default:
		}
		
		// Types
		switch (glEnum)
		{
			case GL_BYTE:
				return "GL_BYTE";
			case GL_UNSIGNED_BYTE:
				return "GL_UNSIGNED_BYTE";
			case GL_SHORT:
				return "GL_SHORT";
			case GL_UNSIGNED_SHORT:
				return "GL_UNSIGNED_SHORT";
			case GL_INT:
				return "GL_INT";
			case GL_UNSIGNED_INT:
				return "GL_UNSIGNED_INT";
			case GL_FLOAT:
				return "GL_FLOAT";
			case GL_DOUBLE:
				return "GL_DOUBLE";
			default:
		}
		
		return "GL_UNKNOWN(" + glEnum + ")";
	}
	
	public static int getTypeSize(int glTypeEnum)
	{
		switch (glTypeEnum)
		{
			case GL_BYTE:
			case GL_UNSIGNED_BYTE:
				return 1;
			case GL_SHORT:
			case GL_UNSIGNED_SHORT:
				return 2;
			case GL_INT:
			case GL_UNSIGNED_INT:
				return 4;
			case GL_FLOAT:
				return 4;
			case GL_DOUBLE:
				return 8;
			default:
				throw new IllegalArgumentException("Unknown type enum: " + getString(glTypeEnum));
		}
	}
	
}