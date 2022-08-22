package shadersmodcore.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

public class ShaderPackNone implements IShaderPack {
	
	public ShaderPackNone() {
	}

	@Override
	public void close() {
	}

	@Override
	public InputStream getResourceAsStream(String resName) {
		return null;
	}
}
