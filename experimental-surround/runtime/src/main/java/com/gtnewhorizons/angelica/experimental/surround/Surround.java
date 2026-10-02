package com.gtnewhorizons.angelica.experimental.surround;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Experimental and internal to Angelica; the API may change without notice.
 * <p>
 * Surrounds one method call ({@link #at()} set) or a whole method ({@link #at()} empty) with try/catch/finally.
 * The call form replaces MixinExtras' {@code @WrapOperation} and the whole-method form replaces {@code @WrapMethod},
 * without their per-call {@code Operation} and {@code Object[]} allocation. Use it when the original runs at most once
 * with its original arguments.
 * <p>
 * A surround can:
 * <ul>
 * <li>run code before the original: the entry handler, the annotated method itself;</li>
 * <li>hand state from that code to everything after it: {@link Carry @Surround.Carry} locals;</li>
 * <li>read the call's operands, the target's parameters, and locals at the call: operands,
 * {@link Local @Surround.Local};</li>
 * <li>skip the original and supply its result instead: {@link Skip @Surround.Skip} + {@link Skipped @Surround.Skipped};</li>
 * <li>replace the result after a normal completion: {@link Return @Surround.Return};</li>
 * <li>react to an exception, rethrowing or (calls only) swallowing it and supplying the result:
 * {@link Catch @Surround.Catch};</li>
 * <li>run code on every way out: {@link Finally @Surround.Finally}.</li>
 * </ul>
 * It cannot call the original twice or with different arguments.
 *
 * <h2>Example</h2>
 * <pre>
 * &#64;Surround(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V")
 * private void enter(EntityItem entity, double x, double y, double z, float yaw, float partialTicks) {
 *     &#64;Surround.Carry int prevItemId = ItemIdManager.getItemId();
 *     ItemIdManager.setItemId(entity.getEntityItem());
 * }
 *
 * &#64;Surround.Finally
 * private void restore(&#64;Surround.Carry int prevItemId) {
 *     ItemIdManager.setItemIdRaw(prevItemId);
 * }
 * </pre>
 *
 * <h2>Handlers</h2>
 * The annotated method is the entry handler. Exit handlers are methods in the same mixin whose {@code value} is this
 * surround's {@link #id()}:
 * <pre>
 * &#64;Surround         void enter(operands, &#64;Local params)                         before the original
 * &#64;Surround.Skipped T    skipped(operands, &#64;Local params, &#64;Carry params)          instead of the original
 * &#64;Surround.Return  T    ret(T result, operands, &#64;Local params, &#64;Carry params)    after it completes normally
 * &#64;Surround.Catch   void caught(E error, operands, &#64;Local params, &#64;Carry params)   when it throws E
 * &#64;Surround.Finally void always(operands, &#64;Local params, &#64;Carry params)         on every way out
 * </pre>
 * {@code T} is the return type of the call or method. When it is {@code void}, {@code ret} takes no result and
 * {@code Skipped} returns {@code void}.
 * <ul>
 * <li>The entry handler returns {@code void}. Exit handlers are static exactly when the entry handler is.</li>
 * <li>Per surround: at most one {@code Skipped}, one {@code Return} and one {@code Finally}, and one {@code Catch} per
 * exception type. A method has one role.</li>
 * <li>{@link #id()} is required when the mixin declares more than one {@code @Surround}.</li>
 * </ul>
 *
 * <h2>Parameters</h2>
 * Declare them in this order, each group optional:
 * <ol>
 * <li>Operands, as a leading prefix. For a call: the receiver (unless static), then the arguments. A reference
 * operand may be declared as a supertype. For a whole method: the target's parameters.</li>
 * <li>{@link Local @Surround.Local} parameters (call form only). Exit handlers see the value as of entry.</li>
 * <li>{@link Carry @Surround.Carry} parameters (exit handlers only).</li>
 * </ol>
 *
 * <h2>Carrying state</h2>
 * Exit handlers see entry-handler locals only through {@link Carry @Surround.Carry}; see there for the rules. Two
 * carries of one kind are told apart by name:
 * <pre>
 * &#64;Surround(method = ..., at = &#64;At(value = "INVOKE", target = ...))
 * private void enter() {
 *     &#64;Surround.Carry("depth") int depth = GLStateManager.pushState(StateSet.BLEND);
 *     &#64;Surround.Carry("prev") int prevProgram = GLStateManager.getActiveProgram();
 *     &#64;Surround.Carry Class&lt;?&gt; prevRenderable = TesrAttribution.currentRenderable;
 * }
 *
 * &#64;Surround.Finally
 * private void exit(&#64;Surround.Carry("depth") int depth, &#64;Surround.Carry("prev") int prevProgram,
 *                   &#64;Surround.Carry Class&lt;?&gt; prevRenderable) { ... }
 * </pre>
 *
 * <h2>Entry-handler body</h2>
 * <ul>
 * <li>Every path must fall through to the end: no early {@code return}. Branch with {@code if} instead.</li>
 * <li>Do not assign its parameters. To change arguments, use {@code @ModifyVariable} or {@code @WrapMethod}.</li>
 * <li>In the call form it is inlined at every matched call, as are the exit-handler calls. Keep it short: move bulky
 * logic into a helper and carry the helper's result. Huge targets stop being JIT-compiled past 8000 bytes.</li>
 * </ul>
 *
 * <h2>Skipping</h2>
 * A {@link Skip @Surround.Skip} local in the entry handler decides whether the original runs; see there for the rules.
 *
 * <h2>Order and exceptions</h2>
 * <ol>
 * <li>The entry handler. If it throws, nothing else runs.</li>
 * <li>The original, or {@link Skipped} when skipped.</li>
 * <li>{@link Return}, only if the original completed normally (not after {@code Skipped}). Its return value replaces
 * the result.</li>
 * <li>{@link Finally}, on every way out: normal, after a catch, or rethrowing.</li>
 * </ol>
 * {@link Catch} covers step 2, not {@code Return}, and only the most specific matching one runs. It rethrows when
 * it returns, unless {@link Catch#handle()} is set (call form only): the exception then ends there, and the
 * handler's return value becomes the call's result.
 *
 * <h2>Whole-method form</h2>
 * <ul>
 * <li>The original body becomes a private {@code name$surround} method, so stack traces gain that frame.</li>
 * <li>A {@code synchronized} target holds its lock across the handlers too.</li>
 * <li>An entry handler that calls the target goes through the surround again.</li>
 * <li>{@code @Local}, {@link #slice()} and {@code handle} are call-form only.</li>
 * </ul>
 *
 * <h2>With other injectors</h2>
 * <ul>
 * <li>A call surround encloses a {@code @Redirect} or {@code @WrapOperation} on the same call. A whole-method surround
 * encloses every other injection in the method, including call surrounds.</li>
 * <li>Several surrounds on one call or method nest, lowest priority innermost. An outer skip skips the inner ones
 * entirely, and {@code Return} replacements chain from inner to outer.</li>
 * <li>A matched call that another injector removed is skipped with a warning.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Surround {

    String[] method() default {};

    /**
     * Calls to surround, as INVOKE-style {@code @At} without shift or by; each match is surrounded separately. Empty
     * surrounds the whole method.
     */
    At[] at() default {};

    /**
     * As for {@code @Inject}; calls only.
     */
    Slice[] slice() default {};

    /**
     * Names this surround for its exit handlers. Required when the mixin declares more than one {@code @Surround};
     * unique per mixin.
     */
    String id() default "";

    boolean remap() default true;

    /**
     * As for {@code @Inject}, as are {@link #expect()} and {@link #allow()}; each surrounded call or whole target counts once.
     */
    int require() default -1;

    int expect() default 1;

    int allow() default -1;

    /**
     * Carries a value from the entry handler to the exit handlers.
     * <p>
     * Annotate a local in the entry handler, then declare a parameter with the same annotation in any exit handler that
     * needs the value:
     * <pre>
     * &#64;Surround(method = ...)
     * private void enter() {
     *     &#64;Surround.Carry int prevItemId = ItemIdManager.getItemId();
     *     ItemIdManager.setItemId(...);
     * }
     *
     * &#64;Surround.Finally
     * private void restore(&#64;Surround.Carry int prevItemId) {
     *     ItemIdManager.setItemIdRaw(prevItemId);
     * }
     * </pre>
     * Declaring the local:
     * <ul>
     * <li>Annotate the declaration itself: {@code @Surround.Carry int x = ...}. Not a parameter of the entry handler
     * (use operands for those), and not part of the type (e.g. an array component).</li>
     * <li>Declare it at the top level of the entry handler and initialize it there. It must still be in scope and
     * definitely assigned when the entry handler ends.</li>
     * </ul>
     * Receiving it:
     * <ul>
     * <li>Carry parameters come last, after operands and {@code @Local} parameters. An exit handler may take any
     * subset.</li>
     * <li>Parameters bind to locals by kind: reference, {@code int}-like ({@code boolean}, {@code byte},
     * {@code char}, {@code short}, {@code int}), {@code long}, {@code float}, {@code double}. If the entry handler
     * carries two locals of one kind, name every one of that kind with the same {@link #value()} on the local and on
     * each parameter that reads it: {@code @Surround.Carry("depth") int depth}. Names are unique within a handler.</li>
     * <li>A reference is cast to the parameter's type on read. Every exit handler must read a given carry as the same
     * type.</li>
     * </ul>
     * Each carry lives in a local slot of the target method, with no allocation, so nested and recursive invocations
     * keep separate values. The entry handler returns {@code void}; carry its results instead.
     */
    @Target({ElementType.TYPE_USE, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Carry {

        String value() default "";
    }

    /**
     * Decides whether the original runs.
     * <p>
     * Annotate one {@code boolean} or {@code int}-like local in the entry handler, declared like a {@link Carry}: at
     * the top level, initialized at its declaration, in scope at the end:
     * <pre>
     * &#64;Surround(method = ..., at = &#64;At(value = "INVOKE", target = ...))
     * private void enter(RenderBlocks renderer, Block block) {
     *     &#64;Surround.Skip boolean skip = FallingBlockRendering.isActive();
     * }
     *
     * &#64;Surround.Skipped
     * private boolean replacement(RenderBlocks renderer, Block block) { ... }
     * </pre>
     * <ul>
     * <li>If the local is nonzero when the entry handler ends, the original does not run and {@link Skipped} runs in
     * its place; {@link Return} does not run after it, {@link Catch} and {@link Finally} still do.</li>
     * <li>{@link Skipped} is required unless the result is {@code void}, and rejected without a {@code Skip}
     * local.</li>
     * <li>At most one per entry handler.</li>
     * </ul>
     */
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Skip {
    }

    /**
     * Supplies the result in place of a skipped original. Required unless that result is {@code void}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Skipped {

        /**
         * The {@link Surround#id()} of the surround this handler belongs to; empty for the default.
         */
        String value() default "";
    }

    /**
     * Runs after the original completes normally. Takes the result first (none for {@code void}) and returns its
     * replacement.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Return {

        /**
         * The {@link Surround#id()} of the surround this handler belongs to; empty for the default.
         */
        String value() default "";
    }

    /**
     * Runs when the original or {@link Skipped} throws the first parameter's type, unless a more specific
     * {@code Catch} matches. Rethrows when it returns.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Catch {

        /**
         * The {@link Surround#id()} of the surround this handler belongs to; empty for the default.
         */
        String value() default "";

        /**
         * Call form only: the exception ends here and the returned value becomes the call's result.
         */
        boolean handle() default false;
    }

    /**
     * Runs on every way out.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Finally {

        /**
         * The {@link Surround#id()} of the surround this handler belongs to; empty for the default.
         */
        String value() default "";
    }

    /**
     * As MixinExtras' {@code @Local}, read at the call; exit handlers see its value at entry. Calls only.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Local {

        int ordinal() default -1;

        int index() default -1;

        String[] name() default {};

        boolean argsOnly() default false;
    }
}
