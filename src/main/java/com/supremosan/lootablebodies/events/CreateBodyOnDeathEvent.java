package com.supremosan.lootablebodies.events;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig.ItemsLossMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.supremosan.lootablebodies.LootableBodies;
import com.supremosan.lootablebodies.components.BodySource;
import com.supremosan.lootablebodies.system.BodyManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CreateBodyOnDeathEvent extends DeathSystems.OnDeathSystem {
    @Override
    public @Nonnull Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.AFTER, DeathSystems.PlayerDropItemsConfig.class),
                new SystemDependency<>(Order.BEFORE, DeathSystems.DropPlayerDeathItems.class)
        );
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        if (player.getGameMode() == GameMode.Creative) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        World world = store.getExternalData().getWorld();

        if (!LootableBodies.isBodySpawnAllowed(world)) {
            return;
        }

        // Use the state configured by PlayerDropItemsConfig (or any other plugin override),
        // keeping the mod in sync with what the death screen and drop systems observe.
        ItemsLossMode lossMode = component.getItemsLossMode();
        if (lossMode == ItemsLossMode.NONE) {
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
        InventoryComponent.Utility utilityComp = store.getComponent(ref, InventoryComponent.Utility.getComponentType());

        ItemContainer armorContainer = armorComp != null ? armorComp.getInventory() : null;
        ItemContainer storageContainer = storageComp != null ? storageComp.getInventory() : null;
        ItemContainer hotbarContainer = hotbarComp != null ? hotbarComp.getInventory() : null;
        ItemContainer backpackContainer = backpackComp != null ? backpackComp.getInventory() : null;
        ItemContainer utilityContainer = utilityComp != null ? utilityComp.getInventory() : null;

        component.setDisplayDataOnDeathScreen(true);

        ItemStack[] storageItems = createEmptySnapshot(storageContainer);
        ItemStack[] hotbarItems = createEmptySnapshot(hotbarContainer);
        ItemStack[] backpackItems = createEmptySnapshot(backpackContainer);
        ItemStack[] armorItems = createEmptySnapshot(armorContainer);
        ItemStack[] utilityItems = createEmptySnapshot(utilityContainer);

        List<ItemStack> itemsToDrop = new ObjectArrayList<>();

        switch (lossMode) {
            case ALL: {
                collectAllLostItems(storageContainer, storageItems, itemsToDrop);
                collectAllLostItems(hotbarContainer, hotbarItems, itemsToDrop);
                collectAllLostItems(backpackContainer, backpackItems, itemsToDrop);
                collectAllLostItems(armorContainer, armorItems, itemsToDrop);
                collectAllLostItems(utilityContainer, utilityItems, itemsToDrop);
                break;
            }

            case CONFIGURED: {
                ItemContainer[] allContainers = new ItemContainer[]{
                        armorContainer,
                        storageContainer,
                        hotbarContainer,
                        backpackContainer,
                        utilityContainer
                };

                if (component.getItemsDurabilityLossPercentage() > 0.0D) {
                    double durabilityLossRatio = component.getItemsDurabilityLossPercentage() / 100.0D;
                    boolean hasArmorBroken = false;

                    for (ItemContainer itemContainer : allContainers) {
                        if (itemContainer == null) continue;

                        for (short i = 0; i < itemContainer.getCapacity(); ++i) {
                            ItemStack itemStack = itemContainer.getItemStack(i);
                            if (ItemStack.isEmpty(itemStack) || itemStack.isBroken()) continue;

                            if (!itemStack.getItem().getDurabilityLossOnDeath()) continue;

                            double durabilityLoss = itemStack.getMaxDurability() * durabilityLossRatio;
                            ItemStack updatedItemStack = itemStack.withIncreasedDurability(-durabilityLoss);
                            ItemStackSlotTransaction transaction = itemContainer.replaceItemStackInSlot(i, itemStack, updatedItemStack);

                            if (transaction.getSlotAfter() == null) continue;

                            if (transaction.getSlotAfter().isBroken() && itemStack.getItem().getArmor() != null) {
                                hasArmorBroken = true;
                            }
                        }
                    }

                    if (hasArmorBroken) {
                        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            statMap.getStatModifiersManager().scheduleRecalculate();
                        }
                    }
                }

                double itemsAmountLossPercentage = component.getItemsAmountLossPercentage();
                if (itemsAmountLossPercentage > 0.0D) {
                    double itemAmountLossRatio = itemsAmountLossPercentage / 100.0D;
                    collectConfiguredLostItems(storageContainer, storageItems, itemsToDrop, itemAmountLossRatio);
                    collectConfiguredLostItems(hotbarContainer, hotbarItems, itemsToDrop, itemAmountLossRatio);
                    collectConfiguredLostItems(backpackContainer, backpackItems, itemsToDrop, itemAmountLossRatio);
                    collectConfiguredLostItems(armorContainer, armorItems, itemsToDrop, itemAmountLossRatio);
                    collectConfiguredLostItems(utilityContainer, utilityItems, itemsToDrop, itemAmountLossRatio);
                }
                break;
            }

            case NONE:
                break;
        }

        component.setItemsLossMode(ItemsLossMode.NONE);
        component.setItemsAmountLossPercentage(0.0D);
        component.setItemsDurabilityLossPercentage(0.0D);

        if (!itemsToDrop.isEmpty()) {
            component.setItemsLostOnDeath(itemsToDrop);
            world.execute(() -> {
                if (!ref.isValid()) return;
                BodyManager.spawnBody(store, ref, storageItems, hotbarItems, backpackItems, armorItems,
                        utilityItems, BodySource.DEATH);
            });
        }
    }

    @Nullable
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    private ItemStack[] createEmptySnapshot(@Nullable ItemContainer container) {
        if (container == null) {
            return new ItemStack[0];
        }
        return new ItemStack[container.getCapacity()];
    }

    private void collectAllLostItems(@Nullable ItemContainer container, @Nonnull ItemStack[] bodyItems, @Nonnull List<ItemStack> itemsToDrop) {
        if (container == null) return;

        for (short i = 0; i < container.getCapacity(); ++i) {
            ItemStack stack = container.getItemStack(i);
            if (ItemStack.isEmpty(stack) || !stack.getItem().dropsOnDeath()) continue;

            bodyItems[i] = stack;
            itemsToDrop.add(stack);
            container.removeItemStackFromSlot(i);
        }
    }

    private void collectConfiguredLostItems(@Nullable ItemContainer container, @Nonnull ItemStack[] bodyItems, @Nonnull List<ItemStack> itemsToDrop, double itemAmountLossRatio) {
        if (container == null) return;

        for (short i = 0; i < container.getCapacity(); ++i) {
            ItemStack itemStack = container.getItemStack(i);
            if (ItemStack.isEmpty(itemStack) || !itemStack.getItem().dropsOnDeath()) continue;

            int quantityToLose = Math.max(1, MathUtil.floor((double) itemStack.getQuantity() * itemAmountLossRatio));
            ItemStack lostStack = itemStack.withQuantity(quantityToLose);

            bodyItems[i] = lostStack;
            itemsToDrop.add(lostStack);

            int newQuantity = itemStack.getQuantity() - quantityToLose;
            if (newQuantity > 0) {
                container.replaceItemStackInSlot(i, itemStack, itemStack.withQuantity(newQuantity));
            } else {
                container.removeItemStackFromSlot(i);
            }
        }
    }
}
