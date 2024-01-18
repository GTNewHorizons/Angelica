package jss.notfine.render;

import com.gtnewhorizons.angelica.compat.mojang.DefaultVertexFormat;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.VBOManager;
import jss.notfine.core.Settings;
import jss.util.RandomXoshiro256StarStar;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;

public class RenderStars {

    //private static final ResourceLocation locationStarsPng = new ResourceLocation("textures/colormap/stars.png");

    public static void reloadStarRenderList(RenderGlobal render) {
        TessellatorManager.startCapturing();
        renderStars();
        VBOManager.registerVBO(render.starGLCallList, TessellatorManager.stopCapturingToVBO(DefaultVertexFormat.POSITION));
    }

    public static void renderStars() {
        final int totalStars = (int)Settings.TOTAL_STARS.option.getStore();
        if(totalStars <= 0) {
            return;
        }
        final RandomXoshiro256StarStar random = new RandomXoshiro256StarStar(10842L);
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        for(int i = 0; i < totalStars; ++i) {
            float starOnUnitSphereX = random.nextFloat() * 2.0F - 1.0F;
            float starOnUnitSphereY = random.nextFloat() * 2.0F - 1.0F;
            float starOnUnitSphereZ = random.nextFloat() * 2.0F - 1.0F;
            double distanceNormalizer = starOnUnitSphereX * starOnUnitSphereX + starOnUnitSphereY * starOnUnitSphereY + starOnUnitSphereZ * starOnUnitSphereZ;

            if(distanceNormalizer < 1.0D && distanceNormalizer > 0.01D) {
                distanceNormalizer = 1.0D / Math.sqrt(distanceNormalizer);
                starOnUnitSphereX *= distanceNormalizer;
                starOnUnitSphereY *= distanceNormalizer;
                starOnUnitSphereZ *= distanceNormalizer;
                final double starX = starOnUnitSphereX * 100.0D;
                final double starY = starOnUnitSphereY * 100.0D;
                final double starZ = starOnUnitSphereZ * 100.0D;
                final double thetaXZ = Math.atan2(starOnUnitSphereX, starOnUnitSphereZ);
                final double thetaXZSin = Math.sin(thetaXZ);
                final double thetaXZCos = Math.cos(thetaXZ);
                final double starAzimuth = Math.atan2(Math.sqrt(starOnUnitSphereX * starOnUnitSphereX + starOnUnitSphereZ * starOnUnitSphereZ), starOnUnitSphereY);
                final double starAzimuthX = Math.sin(starAzimuth);
                final double starAzimuthZ = Math.cos(starAzimuth);

                final float starSize = 0.15F + random.nextFloat() * 0.1F;
                final double starRotation = random.nextDouble() * Math.PI * 2.0D;
                final double starRotationSin = Math.sin(starRotation);
                final double starRotationCos = Math.cos(starRotation);

                for(int starCorner = 0; starCorner < 4; ++starCorner) {
                    final double cornerOffsetU = (double)((starCorner & 2) - 1) * starSize;
                    final double cornerOffsetV = (double)((starCorner + 1 & 2) - 1) * starSize;
                    final double cornerVerticalOffset = cornerOffsetU * starRotationCos - cornerOffsetV  * starRotationSin;
                    final double cornerHorizontalOffset = cornerOffsetV  * starRotationCos + cornerOffsetU * starRotationSin;
                    final double cornerY = cornerVerticalOffset * starAzimuthX;
                    final double offsetAzimuthal = -cornerVerticalOffset * starAzimuthZ;
                    final double cornerX = offsetAzimuthal * thetaXZSin - cornerHorizontalOffset * thetaXZCos;
                    final double cornerZ = cornerHorizontalOffset * thetaXZSin + offsetAzimuthal * thetaXZCos;
                    tessellator.addVertex(starX + cornerX, starY + cornerY, starZ + cornerZ);
                }
            }
        }
        tessellator.draw();
    }

}
