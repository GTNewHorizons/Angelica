package com.gtnewhorizons.angelica.api.dynamiclights;

/**
 * A dynamic light at a position, for moving things that aren't entities. Make one with
 * {@link DynamicLightsApi#createLight}. Lights terrain and entities like a held torch, so only while dynamic lights are
 * on (see {@link #setLitUnderShaderpacks} for shaderpacks). Main thread only.
 */
public interface PositionalLight {

    /** Moves the light. Cheap enough to call every frame, chunks only rebuild once it's moved a bit. */
    void setPosition(double x, double y, double z);

    /** Brightness 0-15. 0 switches it off. */
    void setLuminance(int luminance);

    double getX();

    double getY();

    double getZ();

    int getLuminance();

    /**
     * Under a shaderpack dynamic lights only show with the player's shader force option, since packs do their own
     * held item and entity light. A light that stands in for real blocks (a rendered structure's lamps) has nothing
     * else to light the world round it, so set this and it lights things under a pack too, like it does without one.
     * Dynamic lights turned off still means off. False by default.
     */
    void setLitUnderShaderpacks(boolean lit);

    boolean isLitUnderShaderpacks();

    /** {@link #setReach} for Angelica's held torch falloff, the default. */
    float TORCH_REACH = -1;

    /**
     * How far it lights at full brightness, 0-15, like block light: a luminance 15 light falls 15 / reach levels a
     * block, so 15 lights exactly like a real block of that luminance and smaller reaches fade out sooner. Dimmer lights
     * get as far as their luminance allows. {@link #TORCH_REACH} (the default) lights like a held torch instead,
     * straight down to nothing at 7.75 blocks whatever the luminance. Fractions are fine, e.g. for a lamp in something
     * drawn at an eighth of its size. Like a move under 0.1 blocks, a change under 0.1 doesn't rebuild the terrain it
     * lights.
     * <p>
     * Further costs more: a moving light rebuilds every chunk section its light gets into, up to 27 at reach 15 against
     * 8 or so for a torch, and never more than 8 at reach 7 or less.
     */
    void setReach(float reach);

    float getReach();

    /** Switches it off for good and drops it from Angelica's lights. */
    void remove();
}
