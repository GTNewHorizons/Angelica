package com.gtnewhorizons.angelica.transform;

import org.objectweb.asm.tree.MethodInsnNode;

public class Names {

    public static class Name {

        String clas;
        String name;
        String desc;

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

    static Clas entityRenderer_;
    static Clas rendererLivingE_;
    static Clas entityLivingBase_;

    static Meth entityRenderer_renderHand;
    static Meth rendererLivingE_doRender;
    static Meth rendererLivingE_renderEquippedItems;

    public static boolean equals(String clas1, String name1, String desc1, String clas2, String name2, String desc2) {
        return clas1.equals(clas2) && name1.equals(name2) && desc1.equals(desc2);
    }

    public static boolean equals(MethodInsnNode node, String owner, String name, String desc) {
        return node.owner.equals(owner) && node.name.equals(name) && node.desc.equals(desc);
    }
}
