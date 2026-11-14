package org.realscale.neoforge.lotm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
public final class LotmAlchemyMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;
    public LotmAlchemyMenu(int id, Inventory inv) {
        this(id, inv, new ItemStackHandler(6), ContainerLevelAccess.NULL, new SimpleContainerData(3));
    }
    public LotmAlchemyMenu(int id, Inventory inv, IItemHandler handler, ContainerLevelAccess access, ContainerData data) {
        super(LotmAlchemyContent.ALCHEMY_MENU.get(), id);
        checkContainerDataCount(data, 3); this.access = access; this.data = data;
        addSlot(new SlotItemHandler(handler,0,35,35));
        addSlot(new SlotItemHandler(handler,1,53,35));
        addSlot(new SlotItemHandler(handler,2,35,53));
        addSlot(new SlotItemHandler(handler,3,53,53));
        addSlot(new SlotItemHandler(handler,4,98,44) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(LotmContent.SPIRIT_EMBER.get()); }
        });
        addSlot(new SlotItemHandler(handler,5,148,44) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++)
            addSlot(new Slot(inv,col+row*9+9,8+col*18,112+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(inv,col,8+col*18,170));
        addDataSlots(data);
    }
    public int getProgress() { return data.get(0); }
    public int getMaxProgress() { return data.get(1); }
    public int getMachineStatus() { return data.get(2); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty=ItemStack.EMPTY; Slot slot=slots.get(index);
        if (slot==null || !slot.hasItem()) return empty;
        ItemStack source=slot.getItem(), copy=source.copy();
        if (index==5) { if (!moveItemStackTo(source,6,42,true)) return empty; }
        else if (index<6) { if (!moveItemStackTo(source,6,42,false)) return empty; }
        else if (source.is(LotmContent.SPIRIT_EMBER.get())) { if (!moveItemStackTo(source,4,5,false)) return empty; }
        else if (!moveItemStackTo(source,0,4,false)) return empty;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player,source); return copy;
    }
    @Override public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access,player,LotmContent.ALCHEMY_APPARATUS.get());
    }
}
