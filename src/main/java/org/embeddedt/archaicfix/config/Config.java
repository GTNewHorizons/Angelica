package org.embeddedt.archaicfix.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {
    /**
     * The mod id that this configuration is associated with.
     */
    String modid();

    /**
     * Root element category, defaults to "general". You must not specify an empty string.
     */
    String category() default "general";

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @interface LangKey {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Comment {
        String[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Ignore {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface DefaultBoolean {
        boolean value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface RangeInt {
        int min() default Integer.MIN_VALUE;

        int max() default Integer.MAX_VALUE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface DefaultInt {
        int value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface RangeFloat {
        float min() default Float.MIN_VALUE;

        float max() default Float.MAX_VALUE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface DefaultFloat {
        float value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface DefaultString {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Pattern {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface DefaultEnum {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface DefaultStringList {
        String[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Name {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @interface RequiresMcRestart {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.TYPE})
    @interface RequiresWorldRestart {
    }
}