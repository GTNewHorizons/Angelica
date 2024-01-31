package com.gtnewhorizons.angelica.rendering;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Marks a ISBRH as thread safe using a thread local instance. Use this when your ISBHR uses a non default constructor.
 * <p></p>
 * To avoid a hard dependency on Angelica, you'll likely want to mark this as an optional interface using:
 * <pre>
 * `@Optional.Interface(modid = "angelica", iface = "com.gtnewhorizons.angelica.rendering.IThreadSafeISBRH")`
 * </pre>
 * <pre>
 *  NOTE: Any tessellator access _must_ be done within the methods of the ISBRH and not cached in the class.
 * </pre>
 */
public interface IThreadSafeISBRH extends ISimpleBlockRenderingHandler {
    IThreadSafeISBRH newInstance();

}
