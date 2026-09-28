package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.iris.IrisDisplayListState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.joml.Vector4f;

public class CapturedRenderingState {
	public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

	@Setter
    @Getter
    private float tickDelta;
	@Getter
	private int textureReloadCount;
	private Runnable textureReloadListener = null;
	@Getter
    private int currentRenderedBlockEntity;
	private Runnable blockEntityIdListener = null;

	@Getter
    private int currentRenderedEntity = -1;
	private Runnable entityIdListener = null;

	@Getter
	private int currentRenderedItem = -1;
	private Runnable itemIdListener = null;
	private final IntArrayList itemStack = new IntArrayList();
	private final IntArrayList entityStack = new IntArrayList();
	private final IntArrayList blockEntityStack = new IntArrayList();
	private final FloatArrayList entityColorStack = new FloatArrayList();

    @Getter
    private final Vector4f currentEntityColor = new Vector4f(0f);
    private Runnable entityColorListener = null;

	private CapturedRenderingState() {
	}

	public void incrementTextureReloadCount() {
		this.textureReloadCount++;

		if (this.textureReloadListener != null) {
			this.textureReloadListener.run();
		}
	}

	public void resetTextureReloadCount() {
		this.textureReloadCount = 0;
	}

    public void setCurrentBlockEntity(int entity) {
		IrisDisplayListState.recordBlockEntity(entity);
		applyCurrentBlockEntity(entity);
	}

	public void setCurrentBlockEntity(Block block, int metadata) {
		IrisDisplayListState.recordBlockEntity(block, metadata);
		final var matches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
		final var meta = matches == null || block == null ? null : matches.get(block);
		applyCurrentBlockEntity(meta == null ? 0 : Math.max(0, meta.get(metadata)));
	}

	public void pushCurrentBlockEntity() {
		IrisDisplayListState.recordBlockEntityScope(true);
		blockEntityStack.push(currentRenderedBlockEntity);
	}

	public void popCurrentBlockEntity() {
		IrisDisplayListState.recordBlockEntityScope(false);
		applyCurrentBlockEntity(blockEntityStack.popInt());
	}

	private void applyCurrentBlockEntity(int entity) {
		if (currentRenderedBlockEntity == entity) return;
		this.currentRenderedBlockEntity = entity;

		if (this.blockEntityIdListener != null) {
			this.blockEntityIdListener.run();
		}
	}

    private void setCurrentEntity(int entity) {
		if (currentRenderedEntity == entity) return;
		this.currentRenderedEntity = entity;

		if (this.entityIdListener != null) {
			this.entityIdListener.run();
		}
	}

	/**
	 * I should have done this a while ago, better solution to not forget these exist in tandem.
	 */
	public void setCurrentEntityAndItem(int entity, int item) {
		IrisDisplayListState.recordEntityAndItem(entity, item);
		setCurrentEntity(entity);
		applyCurrentRenderedItem(item);
	}

	public void setCurrentNamedEntity(NamespacedId entity) {
		IrisDisplayListState.recordNamedEntity(entity);
		final var ids = BlockRenderingSettings.INSTANCE.getEntityIds();
		setCurrentEntity(ids == null ? -1 : ids.applyAsInt(entity));
		applyCurrentRenderedItem(0);
	}

	public void pushCurrentEntityAndItem() {
		IrisDisplayListState.recordEntityScope(true);
		entityStack.push(currentRenderedEntity);
		itemStack.push(currentRenderedItem);
	}

	public void setCurrentRenderedEntity(Entity entity) {
		IrisDisplayListState.recordEntity(entity);
		setCurrentEntity(EntityIdHelper.getEntityId(entity));
		applyCurrentRenderedItem(0);
	}

	public void popCurrentEntityAndItem() {
		IrisDisplayListState.recordEntityScope(false);
		// Restore the playback caller, never the IDs seen while compiling the list.
		setCurrentEntity(entityStack.popInt());
		applyCurrentRenderedItem(itemStack.popInt());
	}

    public void setCurrentEntityColor(float r, float g, float b, float a) {
		IrisDisplayListState.recordEntityColor(r, g, b, a);
		applyCurrentEntityColor(r, g, b, a);
	}

	public void pushCurrentEntityColor() {
		IrisDisplayListState.recordEntityColorScope(true);
		entityColorStack.push(currentEntityColor.x);
		entityColorStack.push(currentEntityColor.y);
		entityColorStack.push(currentEntityColor.z);
		entityColorStack.push(currentEntityColor.w);
	}

	public void popCurrentEntityColor() {
		IrisDisplayListState.recordEntityColorScope(false);
		final float a = entityColorStack.popFloat(), b = entityColorStack.popFloat();
		final float g = entityColorStack.popFloat(), r = entityColorStack.popFloat();
		applyCurrentEntityColor(r, g, b, a);
	}

	private void applyCurrentEntityColor(float r, float g, float b, float a) {
		if (currentEntityColor.equals(r, g, b, a)) return;
        this.currentEntityColor.set(r, g, b, a);

        if (this.entityColorListener != null) {
            this.entityColorListener.run();
        }
    }

	public void setCurrentRenderedItem(int item) {
		IrisDisplayListState.recordItemId(item);
		applyCurrentRenderedItem(item);
	}

	public void pushCurrentRenderedItem() {
		IrisDisplayListState.recordItemScope(true);
		itemStack.push(currentRenderedItem);
	}

	public void setCurrentRenderedItem(ItemStack item) {
		IrisDisplayListState.recordItem(item);
		applyCurrentRenderedItem(ItemMaterialHelper.getMaterialId(item));
	}

	public void setCurrentRenderedItem(Item item, int metadata) {
		IrisDisplayListState.recordItem(item, metadata);
		applyCurrentRenderedItem(ItemMaterialHelper.getMaterialId(item, metadata));
	}

	public void setCurrentNamedItem(NamespacedId item) {
		IrisDisplayListState.recordNamedItem(item);
		final var ids = BlockRenderingSettings.INSTANCE.getItemIds();
		applyCurrentRenderedItem(ids == null ? 0 : ids.applyAsInt(item));
	}

	public void setCurrentRenderedBlockItem(Block block, int metadata) {
		IrisDisplayListState.recordBlockItem(block, metadata);
		applyCurrentRenderedItem(ItemMaterialHelper.getMaterialId(block, metadata));
	}

	public void popCurrentRenderedItem() {
		IrisDisplayListState.recordItemScope(false);
		applyCurrentRenderedItem(itemStack.popInt());
	}

	private void applyCurrentRenderedItem(int item) {
		if (this.currentRenderedItem == item) {
			return;
		}

		this.currentRenderedItem = item;

		if (this.itemIdListener != null) {
			this.itemIdListener.run();
		}
	}

	public ValueUpdateNotifier getEntityIdNotifier() {
		return listener -> this.entityIdListener = listener;
	}

	public ValueUpdateNotifier getBlockEntityIdNotifier() {
		return listener -> this.blockEntityIdListener = listener;
	}

	public ValueUpdateNotifier getItemIdNotifier() {
		return listener -> this.itemIdListener = listener;
	}

	public ValueUpdateNotifier getTextureReloadNotifier() {
		return listener -> this.textureReloadListener = listener;
	}

    public ValueUpdateNotifier getEntityColorNotifier() { return listener -> this.entityColorListener = listener; }
}
