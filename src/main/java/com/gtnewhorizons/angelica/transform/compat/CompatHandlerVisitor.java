package com.gtnewhorizons.angelica.transform.compat;

import com.gtnewhorizons.angelica.transform.compat.handlers.CompatHandler;

public interface CompatHandlerVisitor {

    void visit(CompatHandler handler);

}
