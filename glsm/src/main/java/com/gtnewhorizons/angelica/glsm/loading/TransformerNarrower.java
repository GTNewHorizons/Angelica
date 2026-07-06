package com.gtnewhorizons.angelica.glsm.loading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Narrows overly-broad transformer exclusions that prevent the GLSM redirector from
 * transforming GL calls in mod code. Some coremods register exclusions covering their
 * entire package tree, which inadvertently shields rendering code from redirection.
 * <p>
 * This utility removes the broad exclusion and re-adds narrower ones that cover only
 * the coremod's own transformer/ASM classes, leaving rendering code exposed.
 * <p>
 * Operates on a {@code Set<String>} (the transformer exceptions set from LaunchClassLoader)
 * and a {@code Map<String, Object>} (the Launch blackboard). Callers extract these from
 * the Forge/launchwrapper infrastructure.
 */
public final class TransformerNarrower {

    private static final Logger LOGGER = LogManager.getLogger("GLSM");

    /**
     * A rule that replaces a broad transformer exclusion with narrower ones.
     * @param name       Human-readable name for logging (e.g. "DragonAPI")
     * @param broad      The broad exclusion to remove (e.g. "Reika.DragonAPI.ASM")
     * @param narrow     Narrower exclusions to add in its place
     */
    public record NarrowRule(String name, String broad, List<String> narrow) {}

    /**
     * Apply narrowing rules to the transformer exceptions set.
     *
     * @param exceptions  The mutable Set from LaunchClassLoader.transformerExceptions
     * @param blackboard  The Launch.blackboard map (checked for disable flags)
     * @param keyPrefix   Blackboard key prefix for per-rule opt-out (e.g. "angelica" or "umbra").
     *                    Rule "DragonAPI" checks key "{prefix}.narrow.DragonAPI". If explicitly
     *                    set to Boolean.FALSE, the rule is skipped.
     * @param callerName  Name for log messages (e.g. "Angelica" or "Umbra")
     * @param rules       The narrowing rules to apply
     * @return number of rules that were applied
     */
    public static int narrow(Set<String> exceptions, Map<String, Object> blackboard,
                             String keyPrefix, String callerName, List<NarrowRule> rules) {
        int applied = 0;
        for (NarrowRule rule : rules) {
            final String key = keyPrefix + ".narrow." + rule.name();
            if (Boolean.FALSE.equals(blackboard.get(key))) {
                continue;
            }
            if (exceptions.remove(rule.broad())) {
                for (String narrow : rule.narrow()) {
                    exceptions.add(narrow);
                }
                LOGGER.info("[{}] Narrowed {} transformer exclusion to allow GL redirection", callerName, rule.broad());
                applied++;
            }
        }
        return applied;
    }

    private TransformerNarrower() {}
}
