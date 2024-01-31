package com.gtnewhorizons.angelica.api;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Marks an ISBRH as thread-safe using a thread local instance, allowing Angelica to run it off-thread.
 * Use this when your ISBRH uses a non-default constructor.
 * <p>To avoid a hard dependency on Angelica, you will likely want to mark this as an optional interface using:</p>
 * <pre>
 * @Optional.Interface(modid = "angelica", iface = "com.gtnewhorizons.angelica.api.ThreadSafeISBRHFactory")
 * </pre>
 * 
 * <p><b>Notes</b></p>
 * <ul>
 *   <li>Any tessellator access <i>must</i> be done within the methods of the ISBRH and not cached in the class.</li>
 *   <li>If your class uses a default constructor or is thread-safe by default, consider using the simpler {@link ThreadSafeISBRH}
 *   annotation instead.
 * </ul>
 */
public interface ThreadSafeISBRHFactory extends ISimpleBlockRenderingHandler {
    ThreadSafeISBRHFactory newInstance();
}
