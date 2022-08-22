package shadersmodcore.client;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import shadersmodcore.transform.Names;

import net.minecraft.block.Block;
import net.minecraft.block.BlockIce;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemCloth;

public class ShadersRender {

	public static void setFrustrumPosition(Frustrum frustrum, double x, double y, double z)
	{
		frustrum.setPosition(x, y, z);
	}

	public static void clipRenderersByFrustrum(RenderGlobal renderGlobal, Frustrum frustrum, float par2)
	{
		Shaders.checkGLError("pre clip");
		if (!Shaders.isShadowPass) {
			WorldRenderer[] worldRenderers = renderGlobal.worldRenderers;
	        for (int i = 0; i < worldRenderers.length; ++i)
	        {
	            if (!worldRenderers[i].skipAllRenderPasses())
	            {
	                worldRenderers[i].updateInFrustum(frustrum);
	            }
	        }
		} else {
			WorldRenderer[] worldRenderers = renderGlobal.worldRenderers;
	        for (int i = 0; i < worldRenderers.length; ++i)
	        {
	            if (!worldRenderers[i].skipAllRenderPasses())
	            {
                    worldRenderers[i].isInFrustum = true;
	            }
	        }
		}
	}
	
	public static void renderHand0(EntityRenderer er, float par1, int par2) {
		if (!Shaders.isShadowPass)
		{
			Item item = (Shaders.itemToRender!=null)? Shaders.itemToRender.getItem(): null;
			Block block = (item instanceof ItemBlock)? ((ItemBlock)item).field_150939_a: null; 
			// ItemCloth is for semitransparent block : stained glass, wool
			if (!(item instanceof ItemBlock) && !(block instanceof BlockIce))
			{
				Shaders.readCenterDepth();
				Shaders.beginHand();
				er.renderHand(par1, par2);
				Shaders.endHand();
				Shaders.isHandRendered = true;
			}
		}
	}

	public static void renderHand1(EntityRenderer er, float par1, int par2) {
		if (!Shaders.isShadowPass)
		{
			if (!Shaders.isHandRendered) {
				Shaders.readCenterDepth();
				GL11.glEnable(GL11.GL_BLEND);
				Shaders.beginHand();
				er.renderHand(par1, par2);
				Shaders.endHand();
				Shaders.isHandRendered = true;
			}
		}
	}

	public static void renderItemFP(ItemRenderer itemRenderer, float par1) {
		// clear depth buffer locally
		GL11.glDepthFunc(GL11.GL_GEQUAL);
		GL11.glPushMatrix();
		IntBuffer drawBuffers = Shaders.activeDrawBuffers;
		Shaders.setDrawBuffers(Shaders.drawBuffersNone);
		itemRenderer.renderItemInFirstPerson(par1);
		Shaders.setDrawBuffers(drawBuffers);
		GL11.glPopMatrix();
		GL11.glDepthFunc(GL11.GL_LEQUAL);
		// really render item or hand
		itemRenderer.renderItemInFirstPerson(par1);
	}
	
	public static void renderFPOverlay(EntityRenderer er, float par1, int par2) {
		Shaders.beginFPOverlay();
		er.renderHand(par1, par2);
		Shaders.endFPOverlay();
	}

}

