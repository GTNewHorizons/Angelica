package com.gtnewhorizons.angelica.rendering;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a ISBRH as thread safe - the main renderer instance will be used.
 * <pre>
 * NOTES:
 *  * Any tessellator access _must_ be done within the methods of the ISBRH and not cached in the class.
 *  * Additionally no state/non final variables should be stored in the ISBRH as it is not guaranteed to be thread safe.
 * </pre>
 */
@Documented
@Target({ TYPE })
@Retention(RUNTIME)
public @interface ThreadSafeISBRH {

}
