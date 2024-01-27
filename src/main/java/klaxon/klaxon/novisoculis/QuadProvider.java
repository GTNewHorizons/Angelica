package klaxon.klaxon.novisoculis;

import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraftforge.common.util.ForgeDirection;

public interface QuadProvider {

    List<Quad> getQuads(Block block, int meta, ForgeDirection dir, Random random);
}
