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

package com.seibel.distanthorizons.core.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.seibel.distanthorizons.core.util.math.Vec3i;

/**
 * An (almost) exact copy of Minecraft's
 * Direction enum. <Br><Br>
 *
 * Up <Br>
 * Down <Br>
 * North <Br>
 * South <Br>
 * East <Br>
 * West <Br>
 *
 * @author James Seibel
 * @version 2021-11-13
 */
public enum EDhDirection
{
	/** negative Y */
	DOWN(0, 1, -1, "down", EDhDirection.AxisDirection.NEGATIVE, EDhDirection.Axis.Y, new Vec3i(0, -1, 0)),
	/** positive Y */
	UP(1, 0, -1, "up", EDhDirection.AxisDirection.POSITIVE, EDhDirection.Axis.Y, new Vec3i(0, 1, 0)),
	/** negative Z */
	NORTH(2, 3, 2, "north", EDhDirection.AxisDirection.NEGATIVE, EDhDirection.Axis.Z, new Vec3i(0, 0, -1)),
	/** positive Z */
	SOUTH(3, 2, 0, "south", EDhDirection.AxisDirection.POSITIVE, EDhDirection.Axis.Z, new Vec3i(0, 0, 1)),
	/** negative X */
	WEST(4, 5, 1, "west", EDhDirection.AxisDirection.NEGATIVE, EDhDirection.Axis.X, new Vec3i(-1, 0, 0)),
	/** positive X */
	EAST(5, 4, 3, "east", EDhDirection.AxisDirection.POSITIVE, EDhDirection.Axis.X, new Vec3i(1, 0, 0));
	
	/**
	 * Up, Down, West, East, North, South <br>
	 * Similar to {@link EDhDirection#OPPOSITE_DIRECTIONS}, just with a different order
	 */
	public static final EDhDirection[] CARDINAL_DIRECTIONS = new EDhDirection[]{
			EDhDirection.UP,
			EDhDirection.DOWN,
			EDhDirection.WEST,
			EDhDirection.EAST,
			EDhDirection.NORTH,
			EDhDirection.SOUTH};
	
	/**
	 * Up, Down, South, North, East, West <br>
	 * Similar to {@link EDhDirection#CARDINAL_DIRECTIONS}, just with a different order
	 */
	public static final EDhDirection[] OPPOSITE_DIRECTIONS = new EDhDirection[]{
			EDhDirection.UP,
			EDhDirection.DOWN,
			EDhDirection.SOUTH,
			EDhDirection.NORTH,
			EDhDirection.EAST,
			EDhDirection.WEST};
	
	/** North, South, East, West */ // TODO rename to state this is just X/Z or flat directions
	public static final EDhDirection[] ADJ_DIRECTIONS = new EDhDirection[]{
			EDhDirection.EAST,
			EDhDirection.WEST,
			EDhDirection.SOUTH,
			EDhDirection.NORTH};

//	private final int data3d;
//	private final int oppositeIndex;
//	private final int data2d;
	
	private final String name;
	private final EDhDirection.Axis axis;
	private final EDhDirection.AxisDirection axisDirection;
	private final Vec3i normal;
	private static final EDhDirection[] VALUES = values();
	
	private static final Map<String, EDhDirection> BY_NAME = Arrays.stream(VALUES).collect(Collectors.toMap(EDhDirection::getName, (p_199787_0_) ->
	{
		return p_199787_0_;
	}));

//	private static final LodDirection[] BY_3D_DATA = Arrays.stream(VALUES).sorted(Comparator.comparingInt((p_199790_0_) ->
//	{
//		return p_199790_0_.data3d;
//	})).toArray((p_199788_0_) ->
//	{
//		return new LodDirection[p_199788_0_];
//	});
//	
//	private static final LodDirection[] BY_2D_DATA = Arrays.stream(VALUES).filter((p_199786_0_) ->
//	{
//		return p_199786_0_.getAxis().isHorizontal();
//	}).sorted(Comparator.comparingInt((p_199789_0_) ->
//	{
//		return p_199789_0_.data2d;
//	})).toArray((p_199791_0_) ->
//	{
//		return new LodDirection[p_199791_0_];
//	});

//	private static final Long2ObjectMap<LodDirection> BY_NORMAL = Arrays.stream(VALUES).collect(Collectors.toMap((p_218385_0_) ->
//	{
//		return (new BlockPos(p_218385_0_.getNormal())).asLong();
//	}, (p_218384_0_) ->
//	{
//		return p_218384_0_;
//	}, (p_218386_0_, p_218386_1_) ->
//	{
//		throw new IllegalArgumentException("Duplicate keys");
//	}, Long2ObjectOpenHashMap::new));
	
	
	
	EDhDirection(int p_i46016_3_, int p_i46016_4_, int p_i46016_5_, String p_i46016_6_, EDhDirection.AxisDirection p_i46016_7_, EDhDirection.Axis p_i46016_8_, Vec3i p_i46016_9_)
	{
//		this.data3d = p_i46016_3_;
//		this.data2d = p_i46016_5_;
//		this.oppositeIndex = p_i46016_4_;
		this.name = p_i46016_6_;
		this.axis = p_i46016_8_;
		this.axisDirection = p_i46016_7_;
		this.normal = p_i46016_9_;
	}




//	public static LodDirection[] orderedByNearest(Entity p_196054_0_)
//	{
//		float f = p_196054_0_.getViewXRot(1.0F) * ((float) Math.PI / 180F);
//		float f1 = -p_196054_0_.getViewYRot(1.0F) * ((float) Math.PI / 180F);
//		float f2 = MathHelper.sin(f);
//		float f3 = MathHelper.cos(f);
//		float f4 = MathHelper.sin(f1);
//		float f5 = MathHelper.cos(f1);
//		boolean flag = f4 > 0.0F;
//		boolean flag1 = f2 < 0.0F;
//		boolean flag2 = f5 > 0.0F;
//		float f6 = flag ? f4 : -f4;
//		float f7 = flag1 ? -f2 : f2;
//		float f8 = flag2 ? f5 : -f5;
//		float f9 = f6 * f3;
//		float f10 = f8 * f3;
//		LodDirection lodDirection = flag ? EAST : WEST;
//		LodDirection direction1 = flag1 ? UP : DOWN;
//		LodDirection direction2 = flag2 ? SOUTH : NORTH;
//		if (f6 > f8)
//		{
//			if (f7 > f9)
//			{
//				return makeDirectionArray(direction1, lodDirection, direction2);
//			}
//			else
//			{
//				return f10 > f7 ? makeDirectionArray(lodDirection, direction2, direction1) : makeDirectionArray(lodDirection, direction1, direction2);
//			}
//		}
//		else if (f7 > f10)
//		{
//			return makeDirectionArray(direction1, direction2, lodDirection);
//		}
//		else
//		{
//			return f9 > f7 ? makeDirectionArray(direction2, lodDirection, direction1) : makeDirectionArray(direction2, direction1, lodDirection);
//		}
//	}

//	private static LodDirection[] makeDirectionArray(LodDirection p_196053_0_, LodDirection p_196053_1_, LodDirection p_196053_2_)
//	{
//		return new LodDirection[] { p_196053_0_, p_196053_1_, p_196053_2_, p_196053_2_.getOpposite(), p_196053_1_.getOpposite(), p_196053_0_.getOpposite() };
//	}

//	public static LodDirection rotate(Mat4f p_229385_0_, LodDirection p_229385_1_)
//	{
//		Vec3i Vec3i = p_229385_1_.getNormal();
//		Vector4f vector4f = new Vector4f(Vec3i.getX(), Vec3i.getY(), Vec3i.getZ(), 0.0F);
//		vector4f.transform(p_229385_0_);
//		return getNearest(vector4f.x(), vector4f.y(), vector4f.z());
//	}

//	public Quaternion getRotation()
//	{
//		Quaternion quaternion = Vector3f.XP.rotationDegrees(90.0F);
//		switch (this)
//		{
//		case DOWN:
//			return Vector3f.XP.rotationDegrees(180.0F);
//		case UP:
//			return Quaternion.ONE.copy();
//		case NORTH:
//			quaternion.mul(Vector3f.ZP.rotationDegrees(180.0F));
//			return quaternion;
//		case SOUTH:
//			return quaternion;
//		case WEST:
//			quaternion.mul(Vector3f.ZP.rotationDegrees(90.0F));
//			return quaternion;
//		case EAST:
//		default:
//			quaternion.mul(Vector3f.ZP.rotationDegrees(-90.0F));
//			return quaternion;
//		}
//	}

//	public int get3DDataValue()
//	{
//		return this.data3d;
//	}
//	
//	public int get2DDataValue()
//	{
//		return this.data2d;
//	}
	
	public EDhDirection.AxisDirection getAxisDirection()
	{
		return this.axisDirection;
	}

//	public LodDirection getOpposite()
//	{
//		return from3DDataValue(this.oppositeIndex);
//	}
	
	public EDhDirection getClockWise()
	{
		switch (this)
		{
			case NORTH:
				return EAST;
			case SOUTH:
				return WEST;
			case WEST:
				return NORTH;
			case EAST:
				return SOUTH;
			default:
				throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
		}
	}
	
	public EDhDirection getCounterClockWise()
	{
		switch (this)
		{
			case NORTH:
				return WEST;
			case SOUTH:
				return EAST;
			case WEST:
				return SOUTH;
			case EAST:
				return NORTH;
			default:
				throw new IllegalStateException("Unable to get CCW facing of " + this);
		}
	}
	
	public String getName()
	{
		return this.name;
	}
	
	public EDhDirection.Axis getAxis()
	{
		return this.axis;
	}
	
	public static EDhDirection byName(String name)
	{
		return name == null ? null : BY_NAME.get(name.toLowerCase(Locale.ROOT));
	}

//	public static LodDirection from3DDataValue(int p_82600_0_)
//	{
//		return BY_3D_DATA[MathHelper.abs(p_82600_0_ % BY_3D_DATA.length)];
//	}
//	
//	public static LodDirection from2DDataValue(int p_176731_0_)
//	{
//		return BY_2D_DATA[MathHelper.abs(p_176731_0_ % BY_2D_DATA.length)];
//	}

//	@Nullable
//	public static LodDirection fromNormal(int p_218383_0_, int p_218383_1_, int p_218383_2_)
//	{
//		return BY_NORMAL.get(BlockPos.asLong(p_218383_0_, p_218383_1_, p_218383_2_));
//	}

//	public static LodDirection fromYRot(double p_176733_0_)
//	{
//		return from2DDataValue(MathHelper.floor(p_176733_0_ / 90.0D + 0.5D) & 3);
//	}
	
	public static EDhDirection fromAxisAndDirection(EDhDirection.Axis p_211699_0_, EDhDirection.AxisDirection p_211699_1_)
	{
		switch (p_211699_0_)
		{
			case X:
				return p_211699_1_ == EDhDirection.AxisDirection.POSITIVE ? EAST : WEST;
			case Y:
				return p_211699_1_ == EDhDirection.AxisDirection.POSITIVE ? UP : DOWN;
			case Z:
			default:
				return p_211699_1_ == EDhDirection.AxisDirection.POSITIVE ? SOUTH : NORTH;
		}
	}

//	public float toYRot()
//	{
//		return (this.data2d & 3) * 90;
//	}

//	public static LodDirection getRandom(Random p_239631_0_)
//	{
//		return Util.getRandom(VALUES, p_239631_0_);
//	}

//	public static LodDirection getNearest(double p_210769_0_, double p_210769_2_, double p_210769_4_)
//	{
//		return getNearest((float) p_210769_0_, (float) p_210769_2_, (float) p_210769_4_);
//	}

//	public static LodDirection getNearest(float p_176737_0_, float p_176737_1_, float p_176737_2_)
//	{
//		LodDirection lodDirection = NORTH;
//		float f = Float.MIN_VALUE;
//		
//		for (LodDirection direction1 : VALUES)
//		{
//			float f1 = p_176737_0_ * direction1.normal.x + p_176737_1_ * direction1.normal.y + p_176737_2_ * direction1.normal.z;
//			if (f1 > f)
//			{
//				f = f1;
//				lodDirection = direction1;
//			}
//		}
//		
//		return lodDirection;
//	}
	
	public static EDhDirection get(EDhDirection.AxisDirection p_181076_0_, EDhDirection.Axis p_181076_1_)
	{
		for (EDhDirection lodDirection : VALUES)
		{
			if (lodDirection.getAxisDirection() == p_181076_0_ && lodDirection.getAxis() == p_181076_1_)
			{
				return lodDirection;
			}
		}
		
		throw new IllegalArgumentException("No such direction: " + p_181076_0_ + " " + p_181076_1_);
	}
	
	public Vec3i getNormal()
	{
		return this.normal;
	}

//	public boolean isFacingAngle(float p_243532_1_)
//	{
//		float f = p_243532_1_ * ((float) Math.PI / 180F);
//		float f1 = -MathHelper.sin(f);
//		float f2 = MathHelper.cos(f);
//		return this.normal.getX() * f1 + this.normal.getZ() * f2 > 0.0F;
//	}
	
	public enum Axis implements Predicate<EDhDirection>
	{
		X("x")
				{
					@Override
					public int choose(int x, int y, int z)
					{
						return x;
					}
					
					@Override
					public double choose(double x, double y, double z)
					{
						return x;
					}
				},
		Y("y")
				{
					@Override
					public int choose(int x, int y, int z)
					{
						return y;
					}
					
					@Override
					public double choose(double x, double y, double z)
					{
						return y;
					}
				},
		Z("z")
				{
					@Override
					public int choose(int x, int y, int z)
					{
						return z;
					}
					
					@Override
					public double choose(double x, double y, double z)
					{
						return z;
					}
				};
		
		private static final EDhDirection.Axis[] VALUES = values();
		
		private static final Map<String, EDhDirection.Axis> BY_NAME = Arrays.stream(VALUES).collect(Collectors.toMap(EDhDirection.Axis::getName, (p_199785_0_) ->
		{
			return p_199785_0_;
		}));
		private final String name;
		
		Axis(String name)
		{
			this.name = name;
		}
		
		public static EDhDirection.Axis byName(String name)
		{
			return BY_NAME.get(name.toLowerCase(Locale.ROOT));
		}
		
		public String getName()
		{
			return this.name;
		}
		
		public boolean isVertical()
		{
			return this == Y;
		}
		
		public boolean isHorizontal()
		{
			return this == X || this == Z;
		}
		
		@Override
		public String toString()
		{
			return this.name;
		}

//		public static LodDirection.Axis getRandom(Random p_239634_0_)
//		{
//			return Util.getRandom(VALUES, p_239634_0_);
//		}
		
		@Override
		public boolean test(EDhDirection p_test_1_)
		{
			return p_test_1_ != null && p_test_1_.getAxis() == this;
		}

//		public LodDirection.Plane getPlane()
//		{
//			switch (this)
//			{
//			case X:
//			case Z:
//				return LodDirection.Plane.HORIZONTAL;
//			case Y:
//				return LodDirection.Plane.VERTICAL;
//			default:
//				throw new Error("Someone's been tampering with the universe!");
//			}
//		}
		
		public abstract int choose(int p_196052_1_, int p_196052_2_, int p_196052_3_);
		
		public abstract double choose(double p_196051_1_, double p_196051_3_, double p_196051_5_);
	}
	
	public enum AxisDirection
	{
		POSITIVE(1, "Towards positive"),
		NEGATIVE(-1, "Towards negative");
		
		private final int step;
		private final String name;
		
		AxisDirection(int newStep, String newName)
		{
			this.step = newStep;
			this.name = newName;
		}
		
		public int getStep()
		{
			return this.step;
		}
		
		@Override
		public String toString()
		{
			return this.name;
		}
		
		public EDhDirection.AxisDirection opposite()
		{
			return this == POSITIVE ? NEGATIVE : POSITIVE;
		}
	}

//	public static enum Plane implements Iterable<LodDirection>, Predicate<LodDirection>
//	{
//		HORIZONTAL(new LodDirection[] { LodDirection.NORTH, LodDirection.EAST, LodDirection.SOUTH, LodDirection.WEST }, new LodDirection.Axis[] { LodDirection.Axis.X, LodDirection.Axis.Z }),
//		VERTICAL(new LodDirection[] { LodDirection.UP, LodDirection.DOWN }, new LodDirection.Axis[] { LodDirection.Axis.Y });
//		
//		private final LodDirection[] faces;
//		private final LodDirection.Axis[] axis;
//		
//		private Plane(LodDirection[] p_i49393_3_, LodDirection.Axis[] p_i49393_4_)
//		{
//			this.faces = p_i49393_3_;
//			this.axis = p_i49393_4_;
//		}
//		
//		public LodDirection getRandomDirection(Random p_179518_1_)
//		{
//			return Util.getRandom(this.faces, p_179518_1_);
//		}
//		
//		public LodDirection.Axis getRandomAxis(Random p_244803_1_)
//		{
//			return Util.getRandom(this.axis, p_244803_1_);
//		}
//		
//		@Override
//		public boolean test(@Nullable LodDirection p_test_1_)
//		{
//			return p_test_1_ != null && p_test_1_.getAxis().getPlane() == this;
//		}
//		
//		@Override
//		public Iterator<LodDirection> iterator()
//		{
//			return Iterators.forArray(this.faces);
//		}
//		
//		public Stream<LodDirection> stream()
//		{
//			return Arrays.stream(this.faces);
//		}
//	}
	
	
	
	
	public String getSerializedName()
	{
		return this.name;
	}
	
	@Override
	public String toString()
	{
		return this.name;
	}
	
}
