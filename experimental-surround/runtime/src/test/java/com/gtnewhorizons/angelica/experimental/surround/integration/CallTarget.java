package com.gtnewhorizons.angelica.experimental.surround.integration;

import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

public class CallTarget {

    public static final String CALLEE = "Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Callee;";
    public static final String TWICE = CALLEE + "twice(I)I";

    private final Callee callee = new Callee();
    private final IntUnaryOperator op = new Callee();
    private final Render renderer = new Render();

    public static int staticTarget(int a) {
        return Callee.twiceStatic(a);
    }

    public int interfaceCall(int a) {
        return this.op.applyAsInt(a);
    }

    public int privateCall(int a) {
        return this.priv(a);
    }

    private int priv(int a) {
        Trace.add("priv " + a);
        return a + 1;
    }

    public int all(int a) {
        return this.callee.twice(a) + this.callee.twice(a + 1) + this.callee.twice(a + 2);
    }

    public int sliced(int a) {
        final int first = this.callee.twice(a);
        Callee.marker();
        return first + this.callee.twice(a + 1);
    }

    public void throwingEntry() {
        this.callee.twice(1);
    }

    public int composed(int a) {
        return this.callee.twice(a);
    }

    public boolean render(Entity entity, double x, double y, double z, float yaw, float partial, boolean flag) {
        Render render = null;
        try {
            render = this.renderer;
            if (render != null && entity != null) {
                try {
                    render.doRender(entity, x, y, z, yaw, partial);
                } catch (Throwable throwable1) {
                    throw new ReportedException("Rendering entity in world", throwable1);
                }
                try {
                    render.doRenderShadowAndFire(entity, x, y, z, yaw, partial);
                } catch (Throwable throwable2) {
                    throw new ReportedException("Post-rendering entity in world", throwable2);
                }
                if (flag) {
                    Trace.add("boundingBox " + entity.name);
                }
            }
            return true;
        } catch (Throwable throwable) {
            throw new ReportedException("Rendering entity (outer)", throwable);
        }
    }

    public int byName(int a) {
        final int first = a + 10;
        final int second = a + 20;
        return this.callee.twice(first) + second;
    }

    public long wideLocals(int a) {
        final long big = a * 10L;
        final double half = a / 2.0;
        return this.callee.wide(big, half);
    }

    public int valueBelow(int a) {
        return a + this.callee.explode(a) * 10;
    }

    public int valueSkip(int a) {
        return this.callee.twice(a);
    }

    public void observed(int a) {
        this.callee.maybeBoom(a);
    }

    public int returnAndHandle(int a) {
        return this.callee.explode(a);
    }

    public int skipFinallyCatch(int a) {
        return this.callee.explode(a);
    }

    public int nestedSkip(int a) {
        return this.callee.twice(a);
    }

    public int redirectedSkip(int a) {
        return this.callee.twice(a);
    }

    public int helperSkip(int a) {
        return this.callee.twice(a);
    }

    public long wideSkip(long a, double b) {
        return this.callee.wide(a, b) + 1L;
    }

    public int entryOnlySkip(int a) {
        this.callee.render(a);
        return a;
    }

    public void entryOnlyWhole() {
        Trace.add("entryOnlyWhole");
    }

    public int skipBelowRestored(int a) {
        return 1000 + this.callee.twice(a);
    }

    public int innerSkipOuterReturn(int a) {
        return this.callee.twice(a);
    }

    public int skipInsideNew(int a) {
        return new AtomicInteger(this.callee.twice(a)).get();
    }

    public int movedBody(int a) {
        final int b = a + 1;
        return 1000 + this.callee.twice(b);
    }

    public static class Callee implements IntUnaryOperator {

        public int twice(int a) {
            Trace.add("twice " + a);
            return a * 2;
        }

        public static int twiceStatic(int a) {
            Trace.add("twiceStatic " + a);
            return a * 2;
        }

        public static void marker() {
            Trace.add("marker");
        }

        public long wide(long a, double b) {
            Trace.add("wide " + a + " " + b);
            return a + (long) b;
        }

        @Override
        public int applyAsInt(int a) {
            Trace.add("apply " + a);
            return a + 1;
        }

        public int explode(int a) {
            Trace.add("explode " + a);
            if (a < 0) {
                throw new IllegalStateException("negative " + a);
            }
            return a * 2;
        }

        public void maybeBoom(int a) {
            Trace.add("maybeBoom " + a);
            if (a < 0) {
                throw new IllegalStateException("boom " + a);
            }
        }

        public void render(int a) {
            Trace.add("render " + a);
        }
    }

    public static class Entity {

        public final String name;
        public boolean explode;

        public Entity(String name) {
            this.name = name;
        }
    }

    public static class Render {

        public void doRender(Entity entity, double x, double y, double z, float yaw, float partial) {
            Trace.add("doRender " + entity.name + " " + x + " " + partial);
            if (entity.explode) {
                throw new RuntimeException("gpu");
            }
        }

        public void doRenderShadowAndFire(Entity entity, double x, double y, double z, float yaw, float partial) {
            Trace.add("shadow " + entity.name);
        }
    }

    public static class ReportedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ReportedException(String description, Throwable cause) {
            super(description, cause);
        }
    }
}
