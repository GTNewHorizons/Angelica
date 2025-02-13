package net.coderbot.iris.api;

public interface IIrisAware {

    /**
     * Gets the number of subpasses required to render this block.
     * Note: this has no relation to the vulkan concept, this is just a convenient name.
     */
    int getSubpassCount(int meta);
}
