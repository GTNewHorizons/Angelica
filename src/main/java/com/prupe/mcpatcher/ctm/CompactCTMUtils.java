package com.prupe.mcpatcher.ctm;

import com.prupe.mcpatcher.mal.tile.TileLoader;
import net.minecraft.util.ResourceLocation;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class CompactCTMUtils {

    public static void generateTextures(BufferedImage[] compactIcons, TileOverride override,
                                        ResourceLocation propsLoc, ResourceLocation blankResource){
        BufferedImage cFull = compactIcons[0]; // Full
        BufferedImage cNone = compactIcons[1]; // None
        BufferedImage cVert = compactIcons[2]; // Vertical
        BufferedImage cHori = compactIcons[3]; // Horizontal
        BufferedImage cCorn = compactIcons[4]; // Corner

        BufferedImage[] expanded = new BufferedImage[47];

        // Generate all 47 textures to match normal CTM
        expanded[0]  = cFull;
        expanded[1]  = generate(cFull, cHori, cFull, cHori);
        expanded[2]  = cHori;
        expanded[3]  = generate(cHori, cFull, cHori, cFull);
        expanded[4]  = generate(cFull, cHori, cVert, cCorn);
        expanded[5]  = generate(cHori, cFull, cCorn, cVert);
        expanded[6]  = generate(cVert, cCorn, cVert, cCorn);
        expanded[7]  = generate(cHori, cHori, cCorn, cCorn);
        expanded[8]  = generate(cCorn, cNone, cCorn, cCorn);
        expanded[9]  = generate(cCorn, cCorn, cCorn, cNone);
        expanded[10] = generate(cNone, cCorn, cNone, cCorn);
        expanded[11] = generate(cNone, cNone, cCorn, cCorn);
        expanded[12] = generate(cFull, cFull, cVert, cVert);
        expanded[13] = generate(cFull, cHori, cVert, cNone);
        expanded[14] = generate(cHori, cHori, cNone, cNone);
        expanded[15] = generate(cHori, cFull, cNone, cVert);
        expanded[16] = generate(cVert, cCorn, cFull, cHori);
        expanded[17] = generate(cCorn, cVert, cHori, cFull);
        expanded[18] = generate(cCorn, cCorn, cHori, cHori);
        expanded[19] = generate(cCorn, cVert, cCorn, cVert);
        expanded[20] = generate(cNone, cCorn, cCorn, cCorn);
        expanded[21] = generate(cCorn, cCorn, cNone, cCorn);
        expanded[22] = generate(cCorn, cCorn, cNone, cNone);
        expanded[23] = generate(cCorn, cNone, cCorn, cNone);
        expanded[24] = cVert;
        expanded[25] = generate(cVert, cNone, cVert, cNone);
        expanded[26] = cNone;
        expanded[27] = generate(cNone, cVert, cNone, cVert);
        expanded[28] = generate(cVert, cNone, cVert, cNone);
        expanded[29] = generate(cHori, cHori, cNone, cCorn);
        expanded[30] = generate(cVert, cNone, cVert, cCorn);
        expanded[31] = generate(cHori, cHori, cCorn, cNone);
        expanded[32] = generate(cNone, cNone, cNone, cCorn);
        expanded[33] = generate(cNone, cNone, cCorn, cNone);
        expanded[34] = generate(cCorn, cNone, cNone, cCorn);
        expanded[35] = generate(cNone, cCorn, cCorn, cNone);
        expanded[36] = generate(cVert, cVert, cFull, cFull);
        expanded[37] = generate(cVert, cNone, cFull, cHori);
        expanded[38] = generate(cNone, cNone, cHori, cHori);
        expanded[39] = generate(cNone, cVert, cHori, cFull);
        expanded[40] = generate(cCorn, cNone, cHori, cHori);
        expanded[41] = generate(cNone, cVert, cNone, cVert);
        expanded[42] = generate(cNone, cCorn, cHori, cHori);
        expanded[43] = generate(cCorn, cVert, cNone, cVert);
        expanded[44] = generate(cNone, cCorn, cNone, cNone);
        expanded[45] = generate(cCorn, cNone, cNone, cNone);
        expanded[46] = cCorn;

        for (int i = 0; i < expanded.length; i++){
            ResourceLocation resource =
                TileLoader.parseTileAddress(propsLoc, String.valueOf(i), blankResource);
            override.addIcon(resource, expanded[i]);
        }
    }

    private static BufferedImage generate(BufferedImage tl, BufferedImage tr, BufferedImage bl, BufferedImage br){
        int w = tl.getWidth();
        int hw = w/2;
        BufferedImage img = new BufferedImage(tl.getWidth(), tl.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int numFrames = tl.getHeight() / tl.getWidth();
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < numFrames; i++){
            int yOff = w * i;
            copy(tl, g, 0, yOff, hw);
            copy(tr, g, hw, yOff, hw);
            copy(bl, g, 0, yOff + hw, hw);
            copy(br, g, hw, yOff + hw, hw);
        }
        g.dispose();
        return img;
    }

    private static void copy(BufferedImage src, Graphics2D g, int x, int y, int s){
        g.drawImage(src, x, y, x+s, y+s, x, y, x+s, y+s, null);
    }

}
