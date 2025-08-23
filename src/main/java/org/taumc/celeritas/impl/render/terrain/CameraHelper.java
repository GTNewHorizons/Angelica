package org.taumc.celeritas.impl.render.terrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class CameraHelper {
    public static Vector3f getThirdPersonOffset() {
        final Vector3f offset = new Vector3f(); // third person offset
        final Matrix4f inverseModelView = new Matrix4f(ActiveRenderInfo.modelview).invert();
        inverseModelView.transformPosition(offset);
        return offset;
    }

    public static Vector3d getCurrentCameraPosition(double partialTicks) {
        var entityIn = Minecraft.getMinecraft().renderViewEntity;
        return new Vector3d(
            entityIn.prevPosX + (entityIn.posX - entityIn.prevPosX) * partialTicks,
            entityIn.prevPosY + (entityIn.posY - entityIn.prevPosY) * partialTicks,
            entityIn.prevPosZ + (entityIn.posZ - entityIn.prevPosZ) * partialTicks
        );
    }
}
