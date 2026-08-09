package com.gtnewhorizons.angelica.sdlgpu.device;

public final class GpuDeviceLostException extends Error {

    public GpuDeviceLostException(String operation, String sdlError) {
        super("GPU device lost during " + operation + ": " + sdlError
            + ". A lost device cannot be recovered, so the game cannot continue. Please report this at "
            + "https://github.com/GTNewHorizons/Angelica/issues and attach your full log file (logs/fml-client-latest.log)");
    }
}
