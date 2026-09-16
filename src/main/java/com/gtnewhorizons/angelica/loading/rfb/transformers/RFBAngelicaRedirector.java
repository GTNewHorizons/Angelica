package com.gtnewhorizons.angelica.loading.rfb.transformers;

import com.gtnewhorizons.angelica.glsm.loading.RfbModRedirector;
import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;

public class RFBAngelicaRedirector extends RfbModRedirector {

    public RFBAngelicaRedirector() {
        super("redirector", AngelicaRedirector.create());
    }
}
