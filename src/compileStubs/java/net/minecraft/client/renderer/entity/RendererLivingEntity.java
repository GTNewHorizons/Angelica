package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.ModelBase;

public abstract class RendererLivingEntity extends Render {

    public ModelBase mainModel;
    /** The model to be used during the render passes. */
    public ModelBase renderPassModel;
}
