package com.gtnewhorizons.angelica.mixins.interfaces;

public interface ITessellatorInstance {
    float getNormalX();
    float getNormalY();
    float getNormalZ();

    float getMidTextureU();

    float getMidTextureV();

    void discard();
}
