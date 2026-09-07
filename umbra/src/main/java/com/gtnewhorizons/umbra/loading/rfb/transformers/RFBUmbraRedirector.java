package com.gtnewhorizons.umbra.loading.rfb.transformers;

import com.gtnewhorizons.angelica.glsm.loading.RfbModRedirector;
import com.gtnewhorizons.umbra.loading.shared.transformers.UmbraRedirector;

public class RFBUmbraRedirector extends RfbModRedirector {

    public RFBUmbraRedirector() {
        super("umbra-redirector", UmbraRedirector.create());
    }
}
