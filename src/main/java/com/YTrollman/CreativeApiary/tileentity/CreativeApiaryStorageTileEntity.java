package com.YTrollman.CreativeApiary.tileentity;

import static net.minecraft.inventory.container.Container.consideredTheSameItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.YTrollman.CreativeApiary.CreativeApiary;
import org.antlr.v4.runtime.misc.NotNull;

import com.YTrollman.CreativeApiary.config.CreativeApiaryConfig;
import com.YTrollman.CreativeApiary.container.CreativeApiaryStorageContainer;
import com.YTrollman.CreativeApiary.registry.ModTileEntityTypes;
import com.resourcefulbees.resourcefulbees.api.IBeeRegistry;
import com.resourcefulbees.resourcefulbees.api.ICustomBee;
import com.resourcefulbees.resourcefulbees.api.beedata.CustomBeeData;
import com.resourcefulbees.resourcefulbees.config.Config;
import com.resourcefulbees.resourcefulbees.container.AutomationSensitiveItemStackHandler;
import com.resourcefulbees.resourcefulbees.item.BeeJar;
import com.resourcefulbees.resourcefulbees.item.UpgradeItem;
import com.resourcefulbees.resourcefulbees.lib.ApiaryOutput;
import com.resourcefulbees.resourcefulbees.lib.ApiaryTabs;
import com.resourcefulbees.resourcefulbees.lib.BeeConstants;
import com.resourcefulbees.resourcefulbees.lib.NBTConstants;
import com.resourcefulbees.resourcefulbees.registry.BeeRegistry;
import com.resourcefulbees.resourcefulbees.registry.ModItems;
import com.resourcefulbees.resourcefulbees.tileentity.multiblocks.apiary.IApiaryMultiblock;
import com.resourcefulbees.resourcefulbees.utils.BeeInfoUtils;
import com.resourcefulbees.resourcefulbees.utils.MathUtils;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings("deprecation")
public class CreativeApiaryStorageTileEntity extends TileEntity implements INamedContainerProvider, ITickableTileEntity, IApiaryMultiblock {

    private static final IBeeRegistry BEE_REGISTRY = BeeRegistry.getRegistry();

    private BlockPos apiaryPos;
    private CreativeApiaryTileEntity apiary;

    private final AutomationSensitiveItemStackHandler itemStackHandler = new TileStackHandler(110);
    private final LazyOptional<IItemHandler> lazyOptional = LazyOptional.of(this::getItemStackHandler);

    public CreativeApiaryStorageTileEntity() {
        super(ModTileEntityTypes.CREATIVE_APIARY_STORAGE_TILE_ENTITY.get());
    }

    @Nonnull
    @Override
    public TileEntityType<?> getType() {
        return ModTileEntityTypes.CREATIVE_APIARY_STORAGE_TILE_ENTITY.get();
    }

    @Nonnull
    @Override
    public ITextComponent getDisplayName() {
        return new TranslationTextComponent("gui.creativeapiary.creative_apiary_storage");
    }

    @Nullable
    @Override
    public Container createMenu(int i, @Nonnull PlayerInventory playerInventory, @Nonnull PlayerEntity playerEntity) {
        if (level != null)
            return new CreativeApiaryStorageContainer(i, level, worldPosition, playerInventory);
        return null;
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            validateApiaryLink();
        }
    }

    public CreativeApiaryTileEntity getApiary() {
        if (apiaryPos != null && level != null) {  //validate apiary first
            TileEntity tile = level.getBlockEntity(apiaryPos); //get apiary pos
            if (tile instanceof CreativeApiaryTileEntity) { //check tile is an apiary tile
                return (CreativeApiaryTileEntity) tile;
            }
        }
        return null;
    }

    public boolean validateApiaryLink() {
        apiary = getApiary();
        if (apiary == null || apiary.getStoragePos() == null || !apiary.getStoragePos().equals(this.getBlockPos()) || !apiary.isValidApiary(false)) { //check apiary has storage location equal to this and apiary is valid
            apiaryPos = null; //if not set these to null
            return false;
        }
        return true;
    }

    @Override
    public void load(@Nonnull BlockState state, @Nonnull CompoundNBT nbt) {
        super.load(state, nbt);
        this.loadFromNBT(nbt);
    }

    @Nonnull
    @Override
    public CompoundNBT save(@Nonnull CompoundNBT nbt) {
        super.save(nbt);
        return this.saveToNBT(nbt);
    }

    public void loadFromNBT(CompoundNBT nbt) {
        CompoundNBT invTag = nbt.getCompound(NBTConstants.NBT_INVENTORY);
        getItemStackHandler().deserializeNBT(invTag);
        if (nbt.contains(NBTConstants.NBT_APIARY_POS))
            apiaryPos = NBTUtil.readBlockPos(nbt.getCompound(NBTConstants.NBT_APIARY_POS));
    }

    public CompoundNBT saveToNBT(CompoundNBT nbt) {
        CompoundNBT inv = this.getItemStackHandler().serializeNBT();
        nbt.put(NBTConstants.NBT_INVENTORY, inv);
        if (apiaryPos != null)
            nbt.put(NBTConstants.NBT_APIARY_POS, NBTUtil.writeBlockPos(apiaryPos));
        return nbt;
    }

    @Nonnull
    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT nbtTagCompound = new CompoundNBT();
        save(nbtTagCompound);
        return nbtTagCompound;
    }

    @Override
    public void handleUpdateTag(@Nonnull BlockState state, CompoundNBT tag) {
        this.load(state, tag);
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        CompoundNBT nbt = new CompoundNBT();
        if (apiaryPos != null)
            nbt.put(NBTConstants.NBT_APIARY_POS, NBTUtil.writeBlockPos(apiaryPos));
        return new SUpdateTileEntityPacket(worldPosition, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        CompoundNBT nbt = pkt.getTag();
        if (nbt.contains(NBTConstants.NBT_APIARY_POS))
            apiaryPos = NBTUtil.readBlockPos(nbt.getCompound(NBTConstants.NBT_APIARY_POS));
    }

    public void deliverHoneycomb(BeeEntity entity, int apiaryTier) {
        String beeType;
        if (entity instanceof ICustomBee && ((ICustomBee) entity).getBeeData().hasHoneycomb()) {
            beeType = ((ICustomBee) entity).getBeeType();
        } else if (!(entity instanceof ICustomBee)) {
            beeType = BeeConstants.VANILLA_BEE_TYPE;
        } else {
            return;
        }

        ItemStack itemstack;
        ItemStack comb = beeType.equals(BeeConstants.VANILLA_BEE_TYPE) ? new ItemStack(Items.HONEYCOMB) : ((ICustomBee) entity).getBeeData().getCombStack();
        ItemStack combBlock = beeType.equals(BeeConstants.VANILLA_BEE_TYPE) ? new ItemStack(Items.HONEYCOMB_BLOCK) : ((ICustomBee) entity).getBeeData().getCombBlockItemStack();
        int[] outputAmounts = beeType.equals(BeeConstants.VANILLA_BEE_TYPE) ? null : BEE_REGISTRY.getBeeData(beeType).getApiaryOutputAmounts();

        switch (apiaryTier) {
	        case 100:
	            itemstack = (CreativeApiaryConfig.TCREATIVE_APIARY_OUTPUT.get() == ApiaryOutput.BLOCK) ? combBlock.copy() : comb.copy();
                itemstack.setCount(CreativeApiaryConfig.TCREATIVE_APIARY_QUANTITY.get());
                break;
            case 8:
                itemstack = (Config.T4_APIARY_OUTPUT.get() == ApiaryOutput.BLOCK) ? combBlock.copy() : comb.copy();
                itemstack.setCount(outputAmounts != null && outputAmounts[3] != -1 ? outputAmounts[3] : Config.T4_APIARY_QUANTITY.get());
                break;
            case 7:
                itemstack = (Config.T3_APIARY_OUTPUT.get() == ApiaryOutput.BLOCK) ? combBlock.copy() : comb.copy();
                itemstack.setCount(outputAmounts != null && outputAmounts[2] != -1 ? outputAmounts[2] : Config.T3_APIARY_QUANTITY.get());
                break;
            case 6:
                itemstack = (Config.T2_APIARY_OUTPUT.get() == ApiaryOutput.BLOCK) ? combBlock.copy() : comb.copy();
                itemstack.setCount(outputAmounts != null && outputAmounts[1] != -1 ? outputAmounts[1] : Config.T2_APIARY_QUANTITY.get());
                break;
            default:
                itemstack = (Config.T1_APIARY_OUTPUT.get() == ApiaryOutput.BLOCK) ? combBlock.copy() : comb.copy();
                itemstack.setCount(outputAmounts != null && outputAmounts[0] != -1 ? outputAmounts[0] : Config.T1_APIARY_QUANTITY.get());
                break;
        }
        depositItemStack(itemstack);
    }

    public boolean breedComplete(String p1, String p2) {
        if (inventoryHasSpace()) {
            CustomBeeData childBeeData = BEE_REGISTRY.getWeightedChild(p1, p2);
            float breedChance = BeeRegistry.getRegistry().getBreedChance(p1, p2, childBeeData);
            EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(childBeeData.getEntityTypeRegistryID());

            if (level != null && entityType != null) {
                Entity entity = entityType.create(level);
                if (entity != null) {
                    ICustomBee beeEntity = (ICustomBee) entity;
                    CompoundNBT nbt = BeeInfoUtils.createJarBeeTag((BeeEntity) beeEntity, NBTConstants.NBT_ENTITY);
                    ItemStack beeJar = new ItemStack(ModItems.BEE_JAR.get());
                    ItemStack emptyBeeJar = new ItemStack(ModItems.BEE_JAR.get());
                    beeJar.setTag(nbt);
                    BeeJar.renameJar(beeJar, (BeeEntity) beeEntity);
                    // if failed, will deposit empty bee jar
                    float nextFloat = level.random.nextFloat();
                    if (breedChance >= nextFloat) {
                        return depositItemStack(beeJar);
                    } else {
                        return depositItemStack(emptyBeeJar);
                    }
                }
            }
        }

        return false;
    }

    public boolean inventoryHasSpace() {
        for (int i = 1; i <= 108; ++i) {
            if (getItemStackHandler().getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean depositItemStack(ItemStack itemstack) {
        int slotIndex = 1;
        while (!itemstack.isEmpty()) {
            if (slotIndex > 108) {
                break;
            }
            ItemStack slotStack = getItemStackHandler().getStackInSlot(slotIndex);

            int maxStackSize = 16384;

            if (slotStack.isEmpty()) {
                int count = itemstack.getCount();
                slotStack = itemstack.copy();
                if (count > maxStackSize) {
                    slotStack.setCount(maxStackSize);
                    itemstack.setCount(count - maxStackSize);
                } else {
                    itemstack.setCount(0);
                }
                getItemStackHandler().setStackInSlot(slotIndex, slotStack);
            } else if (consideredTheSameItem(itemstack, slotStack)) {
                int j = itemstack.getCount() + slotStack.getCount();
                if (j <= maxStackSize) {
                    itemstack.setCount(0);
                    slotStack.setCount(j);
                    getItemStackHandler().setStackInSlot(slotIndex, slotStack);
                } else if (slotStack.getCount() < maxStackSize) {
                    itemstack.shrink(maxStackSize - slotStack.getCount());
                    slotStack.setCount(maxStackSize);
                    getItemStackHandler().setStackInSlot(slotIndex, slotStack);
                }
            }

            ++slotIndex;
        }

        return itemstack.isEmpty();
    }

    public void rebuildOpenContainers() {
        if (level != null) {
            float f = 5.0F;
            BlockPos pos = this.worldPosition;

            for (PlayerEntity playerentity : level.getEntitiesOfClass(PlayerEntity.class, new AxisAlignedBB(pos.getX() - f, pos.getY() - f, pos.getZ() - f, (pos.getX() + 1) + f, (pos.getY() + 1) + f, (pos.getZ() + 1) + f))) {
                if (playerentity.containerMenu instanceof CreativeApiaryStorageContainer) {
                    CreativeApiaryStorageContainer openContainer = (CreativeApiaryStorageContainer) playerentity.containerMenu;
                    CreativeApiaryStorageTileEntity apiaryStorageTileEntity1 = openContainer.getApiaryStorageTileEntity();
                    if (apiaryStorageTileEntity1 == this) {
                        openContainer.setupSlots(true);
                    }
                }
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    public BlockPos getApiaryPos() {
        return apiaryPos;
    }

    public void setApiaryPos(BlockPos apiaryPos) {
        this.apiaryPos = apiaryPos;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? getLazyOptional().cast() :
                super.getCapability(cap, side);
    }

    public AutomationSensitiveItemStackHandler.IAcceptor getAcceptor() {
        return (slot, stack, automation) -> !automation || (slot == 0 && stack.getItem() instanceof UpgradeItem);
    }

    public AutomationSensitiveItemStackHandler.IRemover getRemover() {
        return (slot, automation) -> !automation || slot > 0 && slot <= 110;
    }

    @Override
    public void switchTab(ServerPlayerEntity player, ApiaryTabs tab) {
        if (level != null && apiaryPos != null) {
            if (tab == ApiaryTabs.MAIN) {
                TileEntity tile = level.getBlockEntity(apiaryPos);
                NetworkHooks.openGui(player, (INamedContainerProvider) tile, apiaryPos);
            } else if (tab == ApiaryTabs.BREED) {
                TileEntity tile = level.getBlockEntity(apiary.getBreederPos());
                NetworkHooks.openGui(player, (INamedContainerProvider) tile, apiary.getBreederPos());
            }
        }
    }

	public @NotNull AutomationSensitiveItemStackHandler getItemStackHandler() {
        return itemStackHandler;
    }

    public LazyOptional<IItemHandler> getLazyOptional() {
        return lazyOptional;
    }

    protected class TileStackHandler extends AutomationSensitiveItemStackHandler {

        protected TileStackHandler(int slots) {
            super(slots);
        }

        @Override
        public IAcceptor getAcceptor() {
            return CreativeApiaryStorageTileEntity.this.getAcceptor();
        }

        @Override
        public IRemover getRemover() {
            return CreativeApiaryStorageTileEntity.this.getRemover();
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            setChanged();
            if (slot == 0) {
                rebuildOpenContainers();
            }
        }
    }
}
