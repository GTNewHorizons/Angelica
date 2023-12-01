package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.coderbot.iris.IrisLogging;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This transformer redirects all Tessellator.instance field accesses to go through our TessellatorManager.
 * As well as redirect some GL calls to our custom GLStateManager
 */
public class RedirectorTransformer implements IClassTransformer {

    private static final boolean ASSERT_MAIN_THREAD = Boolean.parseBoolean(System.getProperty("angelica.assertMainThread", "false"));
    private static final boolean DUMP_CLASSES = Boolean.parseBoolean(System.getProperty("angelica.dumpClass", "false"));
    private static final String Drawable = "org/lwjgl/opengl/Drawable";
    private static final String GLStateManager = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL13 = "org/lwjgl/opengl/GL13";
    private static final String GL14 = "org/lwjgl/opengl/GL14";
    private static final String OpenGlHelper = "net/minecraft/client/renderer/OpenGlHelper";
    private static final String EXTBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";
    private static final String ARBMultiTexture = "org/lwjgl/opengl/ARBMultitexture";
    private static final String TessellatorClass = "net/minecraft/client/renderer/Tessellator";
    private static final String BlockClass = "net/minecraft/block/Block";
    private static final String MinecraftClient = "net.minecraft.client";
    private static final String SplashProgress = "cpw.mods.fml.client.SplashProgress";
    private static final String ThreadedBlockData = "com/gtnewhorizons/angelica/glsm/ThreadedBlockData";
    private static final Set<String> ExcludedMinecraftMainThreadChecks = ImmutableSet.of(
        "startGame", "func_71384_a",
        "initializeTextures", "func_77474_a"
    );

    private static final List<Pair<String, String>> BlockBoundsFields = ImmutableList.of(
        Pair.of("minX", "field_149759_B"),
        Pair.of("minY", "field_149760_C"),
        Pair.of("minZ", "field_149754_D"),
        Pair.of("maxX", "field_149755_E"),
        Pair.of("maxY", "field_149756_F"),
        Pair.of("maxZ", "field_149757_G")
    );

    private static final ClassConstantPoolParser cstPoolParser = new ClassConstantPoolParser(GL11, GL13, GL14, OpenGlHelper, EXTBlendFunc, ARBMultiTexture, TessellatorClass, BlockClass);
    private static final Map<String, Map<String, String>> methodRedirects = new HashMap<>();
    private static final Map<Integer, String> glCapRedirects = new HashMap<>();
    private static final List<String> TransformerExclusions = Arrays.asList(
        "org.lwjgl",
        "com.gtnewhorizons.angelica.glsm.",
        "com.gtnewhorizons.angelica.transform",
        "me.eigenraven.lwjgl3ify"
    );
    private static int remaps = 0;

    static {
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_ALPHA_TEST, "AlphaTest");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_BLEND, "Blend");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_DEPTH_TEST, "DepthTest");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_CULL_FACE, "Cull");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_LIGHTING, "Lighting");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, "Texture");
        glCapRedirects.put(org.lwjgl.opengl.GL11.GL_FOG, "Fog");
        glCapRedirects.put(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL, "RescaleNormal");
        methodRedirects.put(GL11, RedirectMap.newMap()
            .add("glAlphaFunc")
            .add("glBindTexture")
            .add("glBlendFunc")
            .add("glClearColor")
            .add("glColor3b")
            .add("glColor3d")
            .add("glColor3f")
            .add("glColor3ub")
            .add("glColor4b")
            .add("glColor4d")
            .add("glColor4f")
            .add("glColor4ub")
            .add("glColorMask")
            .add("glDeleteTextures")
            .add("glDepthFunc")
            .add("glDepthMask")
            .add("glDrawArrays")
            .add("glEndList")
            .add("glFog")
            .add("glFogf")
            .add("glFogi")
            .add("glNewList")
            .add("glPushAttrib")
            .add("glPopAttrib")
            .add("glShadeModel")
            .add("glTexImage2D")
        );
        methodRedirects.put(GL13, RedirectMap.newMap().add("glActiveTexture"));
        methodRedirects.put(GL14, RedirectMap.newMap().add("glBlendFuncSeparate", "tryBlendFuncSeparate"));
        methodRedirects.put(OpenGlHelper, RedirectMap.newMap()
            .add("glBlendFunc", "tryBlendFuncSeparate")
            .add("func_148821_a", "tryBlendFuncSeparate"));
        methodRedirects.put(EXTBlendFunc, RedirectMap.newMap().add("glBlendFuncSeparateEXT", "tryBlendFuncSeparate"));
        methodRedirects.put(ARBMultiTexture, RedirectMap.newMap().add("glActiveTextureARB"));
    }

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        // Ignore classes that are excluded from transformation - Doesn't fully work without the
        // TransformerExclusions due to some nested classes
        for (String exclusion : TransformerExclusions) {
            if (className.startsWith(exclusion)) {
                return basicClass;
            }
        }

        if (!cstPoolParser.find(basicClass)) {
            return basicClass;
        }

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            if (transformedName.equals("net.minecraft.client.renderer.OpenGlHelper") && (mn.name.equals("glBlendFunc") || mn.name.equals("func_148821_a"))) {
                continue;
            }
            boolean redirectInMethod = false;
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node instanceof MethodInsnNode mNode) {
                    if (mNode.owner.equals(GL11) && (mNode.name.equals("glEnable") || mNode.name.equals("glDisable")) && mNode.desc.equals("(I)V")) {
                        final AbstractInsnNode prevNode = node.getPrevious();
                        String name = null;
                        if (prevNode instanceof LdcInsnNode ldcNode) {
                            name = glCapRedirects.get(((Integer) ldcNode.cst));
                        } else if (prevNode instanceof IntInsnNode intNode) {
                            name = glCapRedirects.get(intNode.operand);
                        }
                        if (name != null) {
                            if (mNode.name.equals("glEnable")) {
                                name = "enable" + name;
                            } else {
                                name = "disable" + name;
                            }
                        }
                        if (IrisLogging.ENABLE_SPAM) {
                            if (name == null) {
                                AngelicaTweaker.LOGGER.info("Redirecting call in {} from GL11.{}(I)V to GLStateManager.{}(I)V", transformedName, mNode.name, mNode.name);
                            } else {
                                AngelicaTweaker.LOGGER.info("Redirecting call in {} from GL11.{}(I)V to GLStateManager.{}()V", transformedName, mNode.name, name);
                            }
                        }
                        mNode.owner = GLStateManager;
                        if (name != null) {
                            mNode.name = name;
                            mNode.desc = "()V";
                            mn.instructions.remove(prevNode);
                        }
                        changed = true;
                        redirectInMethod = true;
                        remaps++;
                    } else if (mNode.owner.startsWith(Drawable) && mNode.name.equals("makeCurrent")) {
                        mNode.setOpcode(Opcodes.INVOKESTATIC);
                        mNode.owner = GLStateManager;
                        mNode.desc = "(L" + Drawable + ";)V";
                        mNode.itf = false;
                        changed = true;
                        if (IrisLogging.ENABLE_SPAM) {
                            AngelicaTweaker.LOGGER.info("Redirecting call in {} to GLStateManager.makeCurrent()", transformedName);
                        }
                    } else {
                        final Map<String, String> redirects = methodRedirects.get(mNode.owner);
                        if (redirects != null && redirects.containsKey(mNode.name)) {
                            if (IrisLogging.ENABLE_SPAM) {
                                final String shortOwner = mNode.owner.substring(mNode.owner.lastIndexOf("/") + 1);
                                AngelicaTweaker.LOGGER.info("Redirecting call in {} from {}.{}{} to GLStateManager.{}{}", transformedName, shortOwner, mNode.name, mNode.desc, redirects.get(mNode.name), mNode.desc);
                            }
                            mNode.owner = GLStateManager;
                            mNode.name = redirects.get(mNode.name);
                            changed = true;
                            redirectInMethod = true;
                            remaps++;
                        }
                    }
                } else if (node.getOpcode() == Opcodes.GETSTATIC && node instanceof FieldInsnNode fNode) {
                    if ((fNode.name.equals("field_78398_a") || fNode.name.equals("instance")) && fNode.owner.equals(TessellatorClass)) {
                        if (IrisLogging.ENABLE_SPAM) {
                            AngelicaTweaker.LOGGER.info("Redirecting Tessellator.instance field in {} to TessellatorManager.get()", transformedName);
                        }
                        mn.instructions.set(node, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/gtnewhorizons/angelica/glsm/TessellatorManager", "get", "()Lnet/minecraft/client/renderer/Tessellator;", false));
                        changed = true;
                    }
                } else if ((node.getOpcode() == Opcodes.GETFIELD || node.getOpcode() == Opcodes.PUTFIELD) && node instanceof FieldInsnNode fNode) {
                    if(fNode.owner.equals(BlockClass)) {
                        Pair<String, String> fieldToRedirect = null;
                        for(Pair<String, String> blockPairs : BlockBoundsFields) {
                            if(fNode.name.equals(blockPairs.getLeft()) || fNode.name.equals(blockPairs.getRight())) {
                                fieldToRedirect = blockPairs;
                                break;
                            }
                        }
                        if(fieldToRedirect != null) {
                            if (IrisLogging.ENABLE_SPAM) {
                                AngelicaTweaker.LOGGER.info("Redirecting Block.{} in {} to thread-safe wrapper", fNode.name, transformedName);
                            }
                            // Perform the redirect
                            fNode.name = fieldToRedirect.getLeft(); // use unobfuscated name
                            fNode.owner = ThreadedBlockData;
                            // Inject getter before the field access, to turn Block -> ThreadedBlockData
                            MethodInsnNode getter = new MethodInsnNode(Opcodes.INVOKESTATIC, ThreadedBlockData, "get", "(L" + BlockClass + ";)L" + ThreadedBlockData + ";", false);
                            if(node.getOpcode() == Opcodes.GETFIELD) {
                                mn.instructions.insertBefore(fNode, getter);
                            } else if(node.getOpcode() == Opcodes.PUTFIELD) {
                                // FIXME: this code assumes doubles
                                // Stack: Block, double
                                InsnList beforePut = new InsnList();
                                beforePut.add(new InsnNode(Opcodes.DUP2_X1));
                                // Stack: double, Block, double
                                beforePut.add(new InsnNode(Opcodes.POP2));
                                // Stack: double, Block
                                beforePut.add(getter);
                                // Stack: double, ThreadedBlockData
                                beforePut.add(new InsnNode(Opcodes.DUP_X2));
                                // Stack: ThreadedBlockData, double, ThreadedBlockData
                                beforePut.add(new InsnNode(Opcodes.POP));
                                // Stack: ThreadedBlockData, double
                                mn.instructions.insertBefore(fNode, beforePut);
                            }
                            changed = true;
                        }

                    }
                }
            }
            if (ASSERT_MAIN_THREAD && redirectInMethod && !transformedName.startsWith(SplashProgress) && !(transformedName.startsWith(MinecraftClient) && ExcludedMinecraftMainThreadChecks.contains(mn.name))) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, GLStateManager, "assertMainThread", "()V", false));
            }
        }

        if (changed) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            saveTransformedClass(bytes, transformedName);
            return bytes;
        }
        return basicClass;
    }

    private File outputDir = null;

    private void saveTransformedClass(final byte[] data, final String transformedName) {
        if (!DUMP_CLASSES) {
            return;
        }
        if (outputDir == null) {
            outputDir = new File(Launch.minecraftHome, "ASM_REDIRECTOR");
            try {
                FileUtils.deleteDirectory(outputDir);
            } catch (IOException ignored) {}
            if (!outputDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outputDir.mkdirs();
            }
        }
        final String fileName = transformedName.replace('.', File.separatorChar);
        final File classFile = new File(outputDir, fileName + ".class");
        final File outDir = classFile.getParentFile();
        if (!outDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
        }
        if (classFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            classFile.delete();
        }
        try (final OutputStream output = Files.newOutputStream(classFile.toPath())) {
            output.write(data);
        } catch (IOException e) {
            AngelicaTweaker.LOGGER.error("Could not save transformed class (byte[]) " + transformedName, e);
        }
    }

    private static class RedirectMap<K> extends HashMap<K, K> {
        public static RedirectMap<String> newMap() {
            return new RedirectMap<>();
        }

        public RedirectMap<K> add(K name) {
            this.put(name, name);
            return this;
        }

        public RedirectMap<K> add(K name, K newName) {
            this.put(name, newName);
            return this;
        }
    }

}
