package jss.notfine.asm.mappings;

public class Names {

    public static class Name {

        public String clas;
        public String name;
        public String desc;

        public Name(String clas, String name, String desc) {
            this.clas = clas;
            this.name = name;
            this.desc = desc;
        }

        public Name set(String clas, String name, String desc) {
            this.clas = clas;
            this.name = name;
            this.desc = desc;
            return this;
        }

        public boolean equals(String clas, String name, String desc) {
            return this.clas.equals(clas) && this.name.equals(name) && this.desc.equals(desc);
        }
    }

    public static class Type extends Name {

        public Type(String desc) {
            super("", "", desc);
        }

        public Type(String name, String desc) {
            super(name, name, desc);
        }
    }

    public static class Clas extends Type {

        public Clas(String name) {
            super(name, "L" + name + ";");
        }

        public boolean equals(String clas) {
            return this.clas.equals(clas);
        }
    }

    public static class Fiel extends Name {

        public Fiel(Clas clas, String name, String desc) {
            super(clas.clas, name, desc);
        }

        public boolean equals(String clas, String name) {
            return this.clas.equals(clas) && this.name.equals(name);
        }
    }

    public static class Meth extends Name {

        public Meth(Clas clas, String name, String desc) {
            super(clas.clas, name, desc);
        }

        public boolean equalsNameDesc(String name, String desc) {
            return this.name.equals(name) && this.desc.equals(desc);
        }
    }

    public static Clas renderBlocks_;

    public static Clas block_;

    public static Clas iBlockAccess_;

    public static Clas worldRenderer_;

    public static Clas entityLivingBase_;

    public static Fiel renderBlocks_colorBlueTopRight;

    public static Fiel renderBlocks_blockAccess;

    public static Meth renderBlocks_renderStandardBlockWithAmbientOcclusion;

    public static Meth renderBlocks_renderStandardBlockWithAmbientOcclusionPartial;

    public static Meth worldRenderer_updateRenderer;

    public static Meth block_getRenderBlockPass;

    public static boolean equals(String clas1, String name1, String desc1, String clas2, String name2, String desc2) {
        return clas1.equals(clas2) && name1.equals(name2) && desc1.equals(desc2);
    }
}
