package shadersmodcore.transform;

import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

public class NamerMcf extends NamerMcp {

	public void setNames()
	{
		setNamesSrg();
		rename("../build/unpacked/conf/");
	}
	
}
