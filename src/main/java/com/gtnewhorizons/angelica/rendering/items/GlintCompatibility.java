package com.gtnewhorizons.angelica.rendering.items;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.client.model.ModelZombie;
import net.minecraft.client.model.ModelZombieVillager;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;

/** Keep separate draws when a foreign mixin can change the state or geometry between glint layers. */
public final class GlintCompatibility {
    private static final String ROOT = "com.gtnewhorizons.angelica.mixins.early.";
    private GlintCompatibility() {}

    public static boolean held() {
        return Held.safe;
    }

    public static boolean armor(ModelBase model) {
        return Armor.safe && Armor.models.get(model.getClass());
    }

    private static final class Armor {
        static final boolean safe = inspectMixins(RendererLivingEntity.class, Set.of(
            ROOT + "angelica.entity.MixinRendererLivingEntity_GlintClock",
            ROOT + "angelica.entity.MixinRendererLivingEntity_ModelPassDraws",
            ROOT + "angelica.entity.MixinRendererLivingEntity_EquippedDraws",
            ROOT + "angelica.optimizations.MixinRendererLivingEntity",
            ROOT + "angelica.bugfixes.MixinRendererLivingEntity_OverlayTint",
            ROOT + "angelica.bugfixes.MixinRendererLivingEntity_DeferredEntityOverlay",
            ROOT + "angelica.bugfixes.MixinRendererLivingEntity_EyeDepth",
            ROOT + "shaders.MixinRendererLivingEntity",
            ROOT + "mcpatcherforge.cit.client.renderer.entity.MixinRenderEntityLiving",
            "com.mitchej123.hodgepodge.mixins.early.minecraft.MixinRendererLivingEntity_HitEffectBrightness",
            "com.mitchej123.hodgepodge.mixins.early.minecraft.MixinRendererLivingEntity_NametagBrightness"),
            name -> name.contains("doRender") || name.contains("func_76986_a") || name.contains("*"));
        static final ClassValue<Boolean> models = new ClassValue<>() {
            @Override protected Boolean computeValue(Class<?> type) {
                if (type != ModelBiped.class && type != ModelZombie.class && type != ModelZombieVillager.class
                    && type != ModelSkeleton.class) return false;
                final Set<String> allowed = Set.of(ROOT + "shaders.MixinModelBiped",
                    ROOT + "angelica.bugfixes.MixinModelSkeleton_LegPelvisZFight",
                    ROOT + "angelica.entity.MixinModelRenderer",
                    "ganymedes01.etfuturum.mixins.early.backlytra.client.MixinModelBiped",
                    "xonin.backhand.mixins.early.minecraft.MixinModelBiped",
                    "com.hfstudio.guidenh.mixins.early.minecraft.MixinModelRendererSceneExportCapture");
                for (Class<?> parent = type; parent != Object.class; parent = parent.getSuperclass()) {
                    if (!inspectMixins(parent, allowed, name -> true, false)) return false;
                }
                return inspectMixins(ModelRenderer.class, allowed, name -> true, false);
            }
        };
    }

    private static final class Held {
        static final boolean safe = inspectMixins(ItemRenderer.class, Set.of(
            ROOT + "angelica.bugfixes.MixinItemRenderer_EdgeDepth",
            ROOT + "angelica.entity.MixinItemRenderer_Instanced",
            ROOT + "angelica.itemrenderer.MixinItemRenderer",
            ROOT + "shaders.MixinHeldItemGlintEdges",
            ROOT + "shaders.MixinItemRenderer_ItemId",
            ROOT + "notfine.glint.MixinItemRenderer",
            ROOT + "mcpatcherforge.cit.client.renderer.MixinItemRenderer",
            ROOT + "mcpatcherforge.cc.client.renderer.MixinItemRenderer",
            // Changes base-item translucency before/after the glint sequence.
            "com.gtnewhorizon.gtnhlib.mixins.early.MixinItemRenderer_Translucency"), GlintCompatibility::targetsHeld);
    }

    private static boolean inspectMixins(Class<?> renderer, Set<String> allowed, Predicate<String> selector) {
        return inspectMixins(renderer, allowed, selector, true);
    }

    private static boolean inspectMixins(Class<?> renderer, Set<String> allowed, Predicate<String> selector, boolean requireOurMixin) {
        try {
            final ClassInfo target = ClassInfo.forName(renderer.getName());
            if (target == null) return false;
            boolean foundOurMixin = false;
            final var mixins = ClassInfo.class.getDeclaredField("mixins");
            mixins.setAccessible(true);
            for (Object entry : (Iterable<?>) mixins.get(target)) {
                final IMixinInfo mixin = (IMixinInfo) entry;
                if (allowed.contains(mixin.getClassName())) {
                    foundOurMixin = true;
                    continue;
                }
                final ClassNode node = new ClassNode();
                final byte[] bytes = Launch.classLoader.getClassBytes(mixin.getClassName());
                if (bytes == null) return false;
                new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if (changesMethod(node, selector)) {
                    GLStateManager.LOGGER.debug("Glint batching for {} disabled by {}", renderer.getSimpleName(), mixin.getClassName());
                    return false;
                }
            }
            return !requireOurMixin || foundOurMixin;
        } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError e) {
            GLStateManager.LOGGER.debug("Could not verify glint mixins; keeping separate draws", e);
            return false;
        }
    }

    static boolean changesMethod(ClassNode mixin, Predicate<String> selector) {
        for (MethodNode method : mixin.methods) {
            if (changesMethod(method, method.visibleAnnotations, selector) || changesMethod(method, method.invisibleAnnotations, selector)) return true;
        }
        return false;
    }

    private static boolean changesMethod(MethodNode method, List<AnnotationNode> annotations, Predicate<String> selector) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/Overwrite;") && selector.test(method.name)) return true;
            if (annotation.values == null) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("target")
                    && (annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                        || annotation.desc.startsWith("Lcom/llamalad7/mixinextras/injector/"))) return true;
                if (!annotation.values.get(i).equals("method")) continue;
                final Object selectors = annotation.values.get(i + 1);
                if (selectors instanceof String name && selector.test(name)) return true;
                if (selectors instanceof List<?> list) for (Object entry : list) {
                    if (entry instanceof String name && selector.test(name)) return true;
                }
            }
        }
        return false;
    }

    static boolean targetsHeld(String selector) {
        if (selector.contains("*")) return true;
        final int descriptor = selector.indexOf('(');
        final String name = descriptor < 0 ? selector : selector.substring(0, descriptor);
        return name.endsWith("renderItem") || name.endsWith("renderItemIn2D")
            || name.endsWith("func_78443_a") || name.endsWith("func_78439_a");
    }

}
