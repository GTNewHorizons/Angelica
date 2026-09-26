package com.gtnewhorizons.angelica.compat.galaxyspace;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.angelica.api.clouds.CloudLayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IRenderHandler;

// Compat should live here, not porting all of this to Galaxy Space :trolley:
public final class GalaxySpaceClouds {

    private static final String VENUS = "galaxyspace.SolarSystem.planets.venus.dimension.WorldProviderVenus";
    private static final String BARNARDA_C = "galaxyspace.BarnardsSystem.planets.barnardaC.dimension.WorldProviderBarnardaC";
    private static final String TCETI_E = "galaxyspace.TCetiSystem.planets.tcetiE.dimension.WorldProviderTCetiE";

    private static final String VENUS_RENDERER =
        "galaxyspace.SolarSystem.planets.venus.dimension.sky.CloudProviderVenus";
    private static final String BARNARDA_C_RENDERER =
        "galaxyspace.BarnardsSystem.planets.barnardaC.dimension.sky.CloudProviderBarnardaC";
    private static final String TCETI_E_RENDERER =
        "galaxyspace.TCetiSystem.planets.tcetiE.dimension.sky.CloudProviderTCetiE";

    private static final ResourceLocation CLOUD_TEXTURE = new ResourceLocation("textures/environment/clouds.png");
    private static final double Z_PHASE = 0.33000001311302185;

    private GalaxySpaceClouds() {}

    public static boolean supports(WorldClient world, IRenderHandler renderer) {
        if (world == null || world.provider == null) return false;
        String expected = switch (world.provider.getClass().getName()) {
            case VENUS -> VENUS_RENDERER;
            case BARNARDA_C -> BARNARDA_C_RENDERER;
            case TCETI_E -> TCETI_E_RENDERER;
            default -> null;
        };
        return expected != null && (renderer == null || expected.equals(renderer.getClass().getName()));
    }

    public static List<CloudLayer> getLayers(WorldClient world, int cloudTicks, float partialTicks) {
        String provider = world.provider.getClass().getName();
        if (VENUS.equals(provider)) {
            return fourLayers("galaxyspace:venus", world.provider.getCloudHeight(), 2.0f, cloudTicks, partialTicks);
        }
        if (BARNARDA_C.equals(provider)) {
            return fourLayers("galaxyspace:barnarda_c", world.provider.getCloudHeight(), 5.0f, cloudTicks, partialTicks);
        }
        if (TCETI_E.equals(provider)) {
            return List.of(new CloudLayer("galaxyspace:tceti_e/0", CLOUD_TEXTURE, world.provider.getCloudHeight(),
                12.0f, 4.0f, 12.0f, offsetX(cloudTicks, partialTicks, 0), Z_PHASE,
                230.0f / 1200.0f, 230.0f / 1200.0f, 230.0f / 1200.0f, 0.9f));
        }
        return List.of();
    }

    private static List<CloudLayer> fourLayers(String id, float baseHeight, float widthStep,
        int cloudTicks, float partialTicks) {
        List<CloudLayer> layers = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            float divisor = 1200.0f + 300.0f * i;
            layers.add(new CloudLayer(id + "/" + i, CLOUD_TEXTURE, baseHeight + 20.0f * i,
                12.0f + widthStep * i, 4.0f, 12.0f, offsetX(cloudTicks, partialTicks, i), Z_PHASE,
                234.0f / divisor, 147.0f / divisor, 9.0f / divisor, 0.9f));
        }
        return layers;
    }

    private static double offsetX(int cloudTicks, float partialTicks, int layer) {
        final double layerFraction = layer / 5.0;
        return (cloudTicks * (1.0 - layerFraction) + layerFraction * partialTicks + layer * 250000.0) * 0.03 / 12.0;
    }
}
