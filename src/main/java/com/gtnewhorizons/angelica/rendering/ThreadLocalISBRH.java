package com.gtnewhorizons.angelica.rendering;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a ISBRH as thread safe with a thread local instance
 * A new instance will be created using the default constructor on each thread
 * NOTE: If your Renderer requires arguments to the constructor do _not_ use this annotation;
 * instead use the {@link IThreadSafeISBRH} interface and implement the `newInstance` method
 * <pre>
 * NOTES:
 *  * Any tessellator access _must_ be done within the methods of the ISBRH and not cached in the class.
 *  * Additionally no state/non final variables should be stored in the ISBRH as it is not guaranteed to be thread safe.
 * </pre>
 */
@Documented
@Target({ TYPE })
@Retention(RUNTIME)
public @interface ThreadLocalISBRH {

}
