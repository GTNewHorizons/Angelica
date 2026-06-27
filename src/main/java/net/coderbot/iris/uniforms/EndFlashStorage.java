package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.etfuturum.EndFlashCompat;
import lombok.Getter;

/**
 * Holds the current and previous-frame End flash intensity.
 */
@Getter
public class EndFlashStorage {
    private float lastEndFlash;
    private float currentEndFlash;

    public void tick() {
        lastEndFlash = currentEndFlash;
        currentEndFlash = EndFlashCompat.getIntensity(CapturedRenderingState.INSTANCE.getTickDelta());
    }

}
