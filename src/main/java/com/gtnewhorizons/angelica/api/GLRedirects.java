package com.gtnewhorizons.angelica.api;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public API for querying Angelica's GL call redirections.
 * <p>
 * Angelica's bytecode transformer redirects GL calls (e.g., {@code GL11.glPushMatrix()}) to
 * the GLSM classes for state tracking and core profile compatibility. This works for normal Java bytecode loaded through the Forge classloader,
 * but callers that bypass class transformation (e.g., Nashorn/script engines, reflection, MethodHandles)
 * need to perform their own redirection.
 * <p>
 * This class provides the redirect mapping data and {@link #redirectScript} for transforming
 * script source text before evaluation.
 *
 * <h3>Example: transforming a Nashorn script before eval</h3>
 * <pre>{@code
 * script = GLRedirects.redirectScript(script);
 * engine.eval(script);
 * }</pre>
 * {@link #getMethodRedirectPattern} and {@link #getTargetReference} remain for callers that rewrite text themselves.
 */
public final class GLRedirects {
    private static final String SHORT_GL_PREFIX;
    private static final Map<String, Map<String, String>> NAMED_CLASS_SHORT_NAME_MAP;
    private static final Map<String, String> GL_PREFIX_METHOD_REDIRECTS;
    private static final Pattern METHOD_REDIRECT_PATTERN;
    private static final Pattern UNQUALIFIED_METHOD_REDIRECT_PATTERN;
    private static final String GLSM_INTERNAL_NAME = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GLSM_IMPORT = "importPackage(Packages.com.gtnewhorizons.angelica.glsm)";
    private static final Pattern GLSM_MEMBER_PATTERN = Pattern.compile("\\bGLStateManager\\.(\\w+)");

    static {
        SHORT_GL_PREFIX = buildShortGLPrefix();
        NAMED_CLASS_SHORT_NAME_MAP = buildNamedClassShortNameMap();
        GL_PREFIX_METHOD_REDIRECTS = GLSMRedirector.getGLPrefixMethodRedirects();
        METHOD_REDIRECT_PATTERN = buildMethodRedirectPattern("\\b");
        UNQUALIFIED_METHOD_REDIRECT_PATTERN = buildMethodRedirectPattern("(?<![\\w.$])");
    }

    private GLRedirects() {}

    /**
     * Returns a precompiled pattern that matches redirected {@code ClassName.methodName} references
     * using short (simple) class names, as they might typically appear in script text.
     *
     * @return compiled regex pattern
     */
    public static Pattern getMethodRedirectPattern() {
        return METHOD_REDIRECT_PATTERN;
    }

    /**
     * Looks up the GLSM method a short-name reference redirects to.
     *
     * @param classAndMethod short class name and method, e.g., {@code "GL11.glPushMatrix"}
     * @return the GLSM method name (e.g., {@code "glPushMatrix"}), or {@code null} if this reference is not redirected
     */
    public static String getTargetMethodName(String classAndMethod) {
        final int dot = classAndMethod.indexOf('.');
        if (dot < 0) return null;

        final String className = classAndMethod.substring(0, dot);
        final String methodName = classAndMethod.substring(dot + 1);

        final Map<String, String> classRedirects = NAMED_CLASS_SHORT_NAME_MAP.get(className);
        if (classRedirects != null) {
            return classRedirects.get(methodName);
        }

        if (className.startsWith(SHORT_GL_PREFIX)) {
            return GL_PREFIX_METHOD_REDIRECTS.get(methodName);
        }

        return null;
    }

    /**
     * Looks up the simple-class-qualified redirect target for a short-name reference.
     *
     * @param classAndMethod short class name and method, e.g., {@code "GL11.glPushMatrix"}
     * @return e.g. {@code "GLSMMatrices.glPushMatrix"}, or {@code null} if this reference is not redirected
     */
    public static String getTargetReference(String classAndMethod) {
        final String method = getTargetMethodName(classAndMethod);
        if (method == null) return null;
        final String owner = GLSMRedirector.getTargetOwner(method);
        return qualify(owner, method);
    }

    private static String qualify(String ownerInternalName, String method) {
        return ownerInternalName.substring(ownerInternalName.lastIndexOf('/') + 1) + "." + method;
    }

    /**
     * Rewrites script text so redirected GL calls and moved {@code GLStateManager.*} methods target their GLSM classes,
     * prepending the GLSM package import when anything changed.
     *
     * @return the rewritten script, or {@code script} itself when nothing changed
     */
    public static String redirectScript(String script) {
        String result = UNQUALIFIED_METHOD_REDIRECT_PATTERN.matcher(script).replaceAll(mr -> {
            final String cls = mr.group(1);
            if (cls.equals("GLStateManager") || cls.startsWith("GLSM")) return Matcher.quoteReplacement(mr.group());
            final String ref = getTargetReference(mr.group());
            return Matcher.quoteReplacement(ref != null ? ref : mr.group());
        });
        result = GLSM_MEMBER_PATTERN.matcher(result).replaceAll(mr -> {
            final String owner = GLSMRedirector.getTargetOwner(mr.group(1));
            if (owner == null || owner.equals(GLSM_INTERNAL_NAME)) return Matcher.quoteReplacement(mr.group());
            return Matcher.quoteReplacement(qualify(owner, mr.group(1)));
        });
        if (result.equals(script)) return script;
        return result.contains(GLSM_IMPORT) ? result : GLSM_IMPORT + ";\n" + result;
    }

    private static String buildShortGLPrefix() {
        final String glPrefix = GLSMRedirector.getGLPrefix();
        return glPrefix.substring(glPrefix.lastIndexOf('/') + 1);
    }

    private static Map<String, Map<String, String>> buildNamedClassShortNameMap() {
        final Map<String, Map<String, String>> named = new HashMap<>();
        for (final var entry : GLSMRedirector.getNamedClassMethodRedirects().entrySet()) {
            final String internalName = entry.getKey();
            final String shortName = internalName.substring(internalName.lastIndexOf('/') + 1);
            named.put(shortName, entry.getValue());
        }
        return named;
    }

    private static Pattern buildMethodRedirectPattern(String lead) {
        final TreeSet<String> alternatives = new TreeSet<>();
        alternatives.add(Pattern.quote(SHORT_GL_PREFIX) + "\\w+");
        for (final String shortName : NAMED_CLASS_SHORT_NAME_MAP.keySet()) {
            alternatives.add(Pattern.quote(shortName));
        }
        final String classAlt = String.join("|", alternatives);
        return Pattern.compile(lead + "(" + classAlt + ")\\.\\w+\\b");
    }
}
