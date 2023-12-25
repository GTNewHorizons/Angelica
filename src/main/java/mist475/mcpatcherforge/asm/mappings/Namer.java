package mist475.mcpatcherforge.asm.mappings;

import java.util.ArrayList;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.Launch;

import mist475.mcpatcherforge.asm.mappings.Names.Clas;
import mist475.mcpatcherforge.asm.mappings.Names.Fiel;
import mist475.mcpatcherforge.asm.mappings.Names.Meth;

/**
 * This class und inherited classes are adapted from
 * <a href=
 * "https://github.com/GTNewHorizons/Angelica/blob/master/src/main/java/com/gtnewhorizons/angelica/transform/Namer.java">Angelica/ShadersMod</a>
 */
public class Namer {

    ArrayList<Clas> ac = new ArrayList<>();
    ArrayList<Fiel> af = new ArrayList<>();
    ArrayList<Meth> am = new ArrayList<>();

    Clas c(String name) {
        Clas x = new Clas(name);
        if (ac != null) ac.add(x);
        return x;
    }

    Fiel f(Clas clas, String name, String desc) {
        Fiel x = new Fiel(clas, name, desc);
        if (af != null) af.add(x);
        return x;
    }

    Fiel f(Clas clas, Fiel fiel) {
        Fiel x = new Fiel(clas, fiel.name, fiel.desc);
        if (af != null) af.add(x);
        return x;
    }

    Meth m(Clas clas, String name, String desc) {
        Meth x = new Meth(clas, name, desc);
        if (am != null) am.add(x);
        return x;
    }

    Meth m(Clas clas, Meth meth) {
        Meth x = new Meth(clas, meth.name, meth.desc);
        if (am != null) am.add(x);
        return x;
    }

    public static void initNames() {
        final boolean obfuscated = !(Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
        AngelicaTweaker.LOGGER.info("Environment obfuscated: {}", obfuscated);
        if (obfuscated) {
            // due to mixin bug we can't use sortingIndex meaning we will run too early to use srg
            // new NamerSrg().setNames();
            //TODO: check if this is still the case in Angelica
            new NamerObf().setNames();
        } else {
            new NamerMcp().setNames();
        }
    }
}
