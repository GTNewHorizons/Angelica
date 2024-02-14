package com.gtnewhorizons.angelica.models.json;

import org.joml.Vector3f;

public class ModelDisplay {

    public static final ModelDisplay DEFAULT = new ModelDisplay(
        new Vector3f(0, 0, 0),
        new Vector3f(0, 0, 0),
        new Vector3f(1, 1, 1)
    );

    private final Vector3f rotation;
    private final Vector3f translation;
    private final Vector3f scale;

    public ModelDisplay(Vector3f rotation, Vector3f translation, Vector3f scale) {

        this.rotation = rotation;
        this.translation = translation;
        this.scale = scale;
    }

    enum Position {
        thirdperson_righthand,
        thirdperson_lefthand,
        firstperson_righthand,
        firstperson_lefthand,
        gui,
        head,
        ground,
        fixed;

        public static Position getByName(String name) {
            return switch (name) {
                case "thirdperson_righthand" -> thirdperson_righthand;
                case "thirdperson_lefthand" -> thirdperson_lefthand;
                case "firstperson_righthand" -> firstperson_righthand;
                case "firstperson_lefthand" -> firstperson_lefthand;
                case "gui" -> gui;
                case "head" -> head;
                case "ground" -> ground;
                case "fixed" -> fixed;
                default -> null;
            };
        }
    }
}
