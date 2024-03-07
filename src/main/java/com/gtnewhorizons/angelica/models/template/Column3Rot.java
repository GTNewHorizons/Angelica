package com.gtnewhorizons.angelica.models.template;

import com.gtnewhorizons.angelica.api.ModelLoader;
import com.gtnewhorizons.angelica.api.QuadProvider;
import com.gtnewhorizons.angelica.api.Variant;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;

import static org.joml.Math.toRadians;

/**
 * Use this to create a full cube column rotatable in 3 directions - vertical, x, and z
 */
public class Column3Rot {

    public final Variant[] variants = new Variant[3];
    public final QuadProvider[] models = new QuadProvider[3];
    private static final Matrix4f ROT_X = new Matrix4f()
        .translation(-.5f, -.5f, -.5f).rotateLocalX(toRadians(90)).translateLocal(.5f, .5f, .5f);
    private static final Matrix4f ROT_Y = new Matrix4f()
        .translation(-.5f, -.5f, -.5f).rotateLocalX(toRadians(90)).rotateLocalY(toRadians(90)).translateLocal(.5f, .5f, .5f);

    public Column3Rot(ResourceLocation model) {

        this.variants[0] = new Variant(model, 0, 0, false);
        this.variants[1] = new Variant(model, 90, 90, false);
        this.variants[2] = new Variant(model, 0, 90, false);

        ModelLoader.registerModels(() -> {
            for (int i = 0; i < 3; ++i)
                this.models[i] = ModelLoader.getModel(this.variants[i]);
        }, this.variants);
    }

    public QuadProvider updown() { return this.models[0]; }
    public QuadProvider eastwest() { return this.models[1]; }
    public QuadProvider northsouth() { return this.models[2]; }
}
