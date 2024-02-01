package com.gtnewhorizons.angelica.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks an ISBRH as thread-safe, allowing Angelica to run it off-thread.
 * 
 * <p>Note: Any tessellator access <i>must</i> be done within the methods of the ISBRH and not cached in the class.</p>
 */
@Documented
@Target({ TYPE })
@Retention(RUNTIME)
public @interface ThreadSafeISBRH {
    /**
     * Specifies whether Angelica should create a thread local instance of the class for each thread.
     * <ul>
     *   <li>If this is set to <tt>true</tt>, a new instance will be created using the default constructor on each thread.</li>
     *   <ul>
     *     <li>Note: If your renderer requires arguments to the constructor, do <i>not</i> use this annotation;
     *     instead use the {@link ThreadSafeISBRHFactory} interface and implement the {@link ThreadSafeISBRHFactory#newInstance} method. </li>
     *   </ul>
     *   <li>If this is set to <tt>false</tt>, the main thread instance will be used on all threads.
     *   Use this option if your renderer class has no state, and can be safely used from multiple threads as is.</li>
     *   <ul>
     *     <li>No state/non final variables should be stored in the ISBRH as it is not guaranteed to be thread safe.</li>
     *   </ul>
     * </ul>
     */
    boolean perThread();
}
