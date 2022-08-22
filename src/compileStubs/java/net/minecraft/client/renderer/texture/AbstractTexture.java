package net.minecraft.client.renderer.texture;

import com.gtnewhorizons.angelica.client.MultiTexID;

public abstract class AbstractTexture implements ITextureObject {

    public int glTextureId = -1;
    public MultiTexID multiTex;

    public int getGlTextureId()
    {
        return 0;
    }

    public MultiTexID getMultiTexID()
    {
        return null;
    }

}
