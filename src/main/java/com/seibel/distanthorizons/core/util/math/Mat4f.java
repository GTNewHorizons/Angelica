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

package com.seibel.distanthorizons.core.util.math;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;

/**
 * An (almost) exact copy of Minecraft's 1.16.5
 * implementation of a 4x4 float matrix.
 *
 * @author James Seibel
 * @version 11-11-2021
 */
public class Mat4f extends DhApiMat4f
{
	
	//==============//
	// constructors //
	//==============//
	
	public Mat4f() { /* all values are 0 */ }
	
	public Mat4f(DhApiMat4f sourceMatrix) { super(sourceMatrix); }
	
	/** Expects the values of the input buffer to be in row major order (AKA rows then columns) */
	public Mat4f(FloatBuffer buffer) { this(buffer.array()); }
	/** Expects the values of the input array to be in row major order (AKA rows then columns) */
	public Mat4f(float[] values) { super(values); }
	
	public Mat4f(Matrix4fc sourceMatrix) { this(convertJomlMatrixToArray(sourceMatrix)); }
	private static float[] convertJomlMatrixToArray(Matrix4fc sourceMatrix)
	{
		FloatBuffer buffer = FloatBuffer.allocate(16);
		
		buffer.put(bufferIndex(0, 0), sourceMatrix.m00());
		buffer.put(bufferIndex(0, 1), sourceMatrix.m01());
		buffer.put(bufferIndex(0, 2), sourceMatrix.m02());
		buffer.put(bufferIndex(0, 3), sourceMatrix.m03());
		
		buffer.put(bufferIndex(1, 0), sourceMatrix.m10());
		buffer.put(bufferIndex(1, 1), sourceMatrix.m11());
		buffer.put(bufferIndex(1, 2), sourceMatrix.m12());
		buffer.put(bufferIndex(1, 3), sourceMatrix.m13());
		
		buffer.put(bufferIndex(2, 0), sourceMatrix.m20());
		buffer.put(bufferIndex(2, 1), sourceMatrix.m21());
		buffer.put(bufferIndex(2, 2), sourceMatrix.m22());
		buffer.put(bufferIndex(2, 3), sourceMatrix.m23());
		
		buffer.put(bufferIndex(3, 0), sourceMatrix.m30());
		buffer.put(bufferIndex(3, 1), sourceMatrix.m31());
		buffer.put(bufferIndex(3, 2), sourceMatrix.m32());
		buffer.put(bufferIndex(3, 3), sourceMatrix.m33());
		
		return buffer.array();
	}
	private static int bufferIndex(int xIndex, int zIndex) { return (zIndex * 4) + xIndex; }
	
	
	public void store(FloatBuffer floatBuffer)
	{
		floatBuffer.put(bufferIndex(0, 0), this.m00);
		floatBuffer.put(bufferIndex(0, 1), this.m01);
		floatBuffer.put(bufferIndex(0, 2), this.m02);
		floatBuffer.put(bufferIndex(0, 3), this.m03);
		floatBuffer.put(bufferIndex(1, 0), this.m10);
		floatBuffer.put(bufferIndex(1, 1), this.m11);
		floatBuffer.put(bufferIndex(1, 2), this.m12);
		floatBuffer.put(bufferIndex(1, 3), this.m13);
		floatBuffer.put(bufferIndex(2, 0), this.m20);
		floatBuffer.put(bufferIndex(2, 1), this.m21);
		floatBuffer.put(bufferIndex(2, 2), this.m22);
		floatBuffer.put(bufferIndex(2, 3), this.m23);
		floatBuffer.put(bufferIndex(3, 0), this.m30);
		floatBuffer.put(bufferIndex(3, 1), this.m31);
		floatBuffer.put(bufferIndex(3, 2), this.m32);
		floatBuffer.put(bufferIndex(3, 3), this.m33);
	}
	
	
	public static Matrix4f createJomlMatrix(DhApiMat4f matrix)
	{
		return new Matrix4f(
				matrix.m00, matrix.m10, matrix.m20, matrix.m30,
				matrix.m01, matrix.m11, matrix.m21, matrix.m31,
				matrix.m02, matrix.m12, matrix.m22, matrix.m32,
				matrix.m03, matrix.m13, matrix.m23, matrix.m33
		);
	}
	public Matrix4f createJomlMatrix()
	{
		return new Matrix4f(
				this.m00, this.m10, this.m20, this.m30,
				this.m01, this.m11, this.m21, this.m31,
				this.m02, this.m12, this.m22, this.m32,
				this.m03, this.m13, this.m23, this.m33
		);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	public static Mat4f perspective(double fov, float widthHeightRatio, float nearClipPlane, float farClipPlane)
	{
		float f = (float) (1.0D / Math.tan(fov * ((float) Math.PI / 180F) / 2.0D));
		Mat4f matrix = new Mat4f();
		matrix.m00 = f / widthHeightRatio;
		matrix.m11 = f;
		matrix.m22 = (farClipPlane + nearClipPlane) / (nearClipPlane - farClipPlane);
		matrix.m32 = -1.0F;
		matrix.m23 = 2.0F * farClipPlane * nearClipPlane / (nearClipPlane - farClipPlane);
		return matrix;
	}
	
	/** originally "translate" from Minecraft's MatrixStack */
	public void multiplyTranslationMatrix(double x, double y, double z)
	{ multiply(createTranslateMatrix((float) x, (float) y, (float) z)); }
	
	public static Mat4f createScaleMatrix(float x, float y, float z)
	{
		Mat4f matrix = new Mat4f();
		matrix.m00 = x;
		matrix.m11 = y;
		matrix.m22 = z;
		matrix.m33 = 1.0F;
		return matrix;
	}
	
	public static Mat4f createTranslateMatrix(float x, float y, float z)
	{
		Mat4f matrix = new Mat4f();
		matrix.m00 = 1.0F;
		matrix.m11 = 1.0F;
		matrix.m22 = 1.0F;
		matrix.m33 = 1.0F;
		matrix.m03 = x;
		matrix.m13 = y;
		matrix.m23 = z;
		return matrix;
	}
	
	
	
	//===============//
	// Forge methods //
	//===============//
	
	public void set(DhApiMat4f mat)
	{
		this.m00 = mat.m00;
		this.m01 = mat.m01;
		this.m02 = mat.m02;
		this.m03 = mat.m03;
		this.m10 = mat.m10;
		this.m11 = mat.m11;
		this.m12 = mat.m12;
		this.m13 = mat.m13;
		this.m20 = mat.m20;
		this.m21 = mat.m21;
		this.m22 = mat.m22;
		this.m23 = mat.m23;
		this.m30 = mat.m30;
		this.m31 = mat.m31;
		this.m32 = mat.m32;
		this.m33 = mat.m33;
	}
	
	public void add(DhApiMat4f other)
	{
		m00 += other.m00;
		m01 += other.m01;
		m02 += other.m02;
		m03 += other.m03;
		m10 += other.m10;
		m11 += other.m11;
		m12 += other.m12;
		m13 += other.m13;
		m20 += other.m20;
		m21 += other.m21;
		m22 += other.m22;
		m23 += other.m23;
		m30 += other.m30;
		m31 += other.m31;
		m32 += other.m32;
		m33 += other.m33;
	}
	
	public void multiplyBackward(DhApiMat4f other)
	{
		DhApiMat4f copy = other.copy();
		copy.multiply(this);
		this.set(copy);
	}
	
	public void setTranslation(float x, float y, float z)
	{
		this.m00 = 1.0F;
		this.m11 = 1.0F;
		this.m22 = 1.0F;
		this.m33 = 1.0F;
		this.m03 = x;
		this.m13 = y;
		this.m23 = z;
	}
	
	/**
	 * Changes the values that store the clipping planes.
	 * Formula for calculating matrix values is the same that OpenGL uses when making matrices.
	 *
	 * @param nearClip New near clipping plane value.
	 * @param farClip New far clipping plane value.
	 */
	public void setClipPlanes(float nearClip, float farClip)
	{
		//convert to matrix values, formula copied from a textbook / openGL specification.
		float matNearClip = -((farClip + nearClip) / (farClip - nearClip));
		float matFarClip = -((2 * farClip * nearClip) / (farClip - nearClip));
		//set new values for the clip planes.
		this.m22 = matNearClip;
		this.m23 = matFarClip;
	}
	
	public Mat4f copy() { return new Mat4f(this); }
	
}
