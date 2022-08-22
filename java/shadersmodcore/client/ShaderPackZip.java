package shadersmodcore.client;

import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ShaderPackZip implements IShaderPack {
	
	protected File packFile;
	protected ZipFile packZipFile; 
	
    public ShaderPackZip(String name, File file)
    {
    	packFile = file;
    	packZipFile = null;
    }
    
	@Override
    public void close()
    {
		if(packZipFile != null)
		{
			try
			{
				packZipFile.close();
			}
			catch (Exception excp)
			{
			}
			packZipFile = null;
		}
    }
    
	@Override
    public InputStream getResourceAsStream(String resName)
    {
        if (packZipFile == null)
        {
        	try
        	{
        		packZipFile = new ZipFile(packFile);
        	}
	        catch (Exception excp)
	        {
	        }
        }
        if (packZipFile != null)
        {
	        try
	        {
	            ZipEntry entry = packZipFile.getEntry(resName.substring(1));
	            if (entry != null)
	            {
	                return packZipFile.getInputStream(entry);
	            }
	        }
	        catch (Exception excp)
	        {
	        }
        }
        return null;
    }

}
