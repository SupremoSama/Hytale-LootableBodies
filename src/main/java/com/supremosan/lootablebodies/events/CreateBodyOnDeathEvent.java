package com.supremosan.lootablebodies.events;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig.ItemsLossMode;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
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
import com.hypixel.hytale.server.core.util.UUIDUtil;
import com.supremosan.lootablebodies.system.BodyManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CreateBodyOnDeathEvent extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public @NonNull Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency(Order.AFTER, DeathSystems.PlayerDropItemsConfig.class),
                new SystemDependency(Order.BEFORE, DeathSystems.DropPlayerDeathItems.class)
        );
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            LOGGER.atInfo().log("[CreateBodyOnDeathEvent] Not a player, skipping");
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            LOGGER.atInfo().log("[CreateBodyOnDeathEvent] player ref not found, skipping");
            return;
        }

        if (player.getGameMode() == GameMode.Creative) {
            LOGGER.atInfo().log("[CreateBodyOnDeathEvent] Creative mode, skipping");
            return;
        }

        UUID uuid = playerRef.getUuid();
        World world = store.getExternalData().getWorld();
        DeathConfig deathConfig = world.getDeathConfig();
        LOGGER.atInfo().log("[CreateBodyOnDeathEvent] ItemsLossMode=%s", deathConfig.getItemsLossMode());
        if (deathConfig.getItemsLossMode() == DeathConfig.ItemsLossMode.NONE) return;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        component.setDisplayDataOnDeathScreen(true);

        ItemContainer[] nonArmorContainers = new ItemContainer[]{
                storageComp != null ? storageComp.getInventory() : null,
                hotbarComp != null ? hotbarComp.getInventory() : null,
                backpackComp != null ? backpackComp.getInventory() : null
        };

        ItemContainer armorContainer = armorComp != null ? armorComp.getInventory() : null;
        ItemContainer[] allContainers = listContainers(armorComp, storageComp, hotbarComp, backpackComp);

        if (deathConfig.getItemsDurabilityLossPercentage() > (double) 0.0F) {
            double durabilityLossRatio = deathConfig.getItemsDurabilityLossPercentage() / (double) 100.0F;
            boolean hasArmorBroken = false;

            for (ItemContainer itemContainer : allContainers) {
                if (itemContainer == null) continue;
                for (short i = 0; i < itemContainer.getCapacity(); ++i) {
                    ItemStack itemStack = itemContainer.getItemStack(i);
                    if (!ItemStack.isEmpty(itemStack) && !itemStack.isBroken()) {
                        double durabilityLoss = itemStack.getMaxDurability() * durabilityLossRatio;
                        ItemStack updatedItemStack = itemStack.withIncreasedDurability(-durabilityLoss);
                        ItemStackSlotTransaction transaction = itemContainer.replaceItemStackInSlot(i, itemStack, updatedItemStack);
                        if (transaction.getSlotAfter() == null) continue;
                        if (transaction.getSlotAfter().isBroken() && itemStack.getItem().getArmor() != null) {
                            hasArmorBroken = true;
                        }
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

        ItemStack[] armorStacks = new ItemStack[armorContainer != null ? armorContainer.getCapacity() : 0];
        if (armorContainer != null) {
            for (short i = 0; i < armorStacks.length; ++i) {
                armorStacks[i] = armorContainer.getItemStack(i);
            }
        }

        List<ItemStack> itemsToDrop = null;

        switch (deathConfig.getItemsLossMode()) {
            case ALL:
                itemsToDrop = new ObjectArrayList<>();
                for (ItemContainer itemContainer : nonArmorContainers) {
                    if (itemContainer == null) continue;
                    for (short i = 0; i < itemContainer.getCapacity(); ++i) {
                        ItemStack stack = itemContainer.getItemStack(i);
                        if (!ItemStack.isEmpty(stack)) {
                            itemsToDrop.add(stack);
                            itemContainer.removeItemStackFromSlot(i);
                        }
                    }
                }
                break;
            case CONFIGURED:
                double itemsAmountLossPercentage = deathConfig.getItemsAmountLossPercentage();
                if (itemsAmountLossPercentage > (double) 0.0F) {
                    double itemAmountLossRatio = itemsAmountLossPercentage / (double) 100.0F;
                    itemsToDrop = new ObjectArrayList<>();

                    for (ItemContainer itemContainer : nonArmorContainers) {
                        if (itemContainer == null) continue;
                        for (short i = 0; i < itemContainer.getCapacity(); ++i) {
                            ItemStack itemStack = itemContainer.getItemStack(i);
                            if (!ItemStack.isEmpty(itemStack) && itemStack.getItem().dropsOnDeath()) {
                                int quantityToLose = Math.max(1, MathUtil.floor((double) itemStack.getQuantity() * itemAmountLossRatio));
                                itemsToDrop.add(itemStack.withQuantity(quantityToLose));
                                int newQuantity = itemStack.getQuantity() - quantityToLose;
                                if (newQuantity > 0) {
                                    itemContainer.replaceItemStackInSlot(i, itemStack, itemStack.withQuantity(newQuantity));
                                } else {
                                    itemContainer.removeItemStackFromSlot(i);
                                }
                            }
                        }
                    }
                }
                break;
            case NONE:
                break;
        }

        LOGGER.atInfo().log("[CreateBodyOnDeathEvent] itemsToDrop=%s", itemsToDrop == null ? "null" : itemsToDrop.size());

        if (itemsToDrop != null && !itemsToDrop.isEmpty()) {
            component.setItemsLostOnDeath(itemsToDrop);
            component.setItemsLossMode(ItemsLossMode.NONE);
            component.setItemsAmountLossPercentage(0.0F);
            component.setItemsDurabilityLossPercentage(0.0F);

            LOGGER.atInfo().log("[CreateBodyOnDeathEvent] Calling BodyManager.spawnBody");
            BodyManager.spawnBody(store, ref, uuid, itemsToDrop, armorStacks);
        } else {
            component.setItemsLossMode(ItemsLossMode.NONE);
            component.setItemsAmountLossPercentage(0.0F);
            component.setItemsDurabilityLossPercentage(0.0F);
        }
    }

    @Nullable
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    private ItemContainer[] listContainers(
            @Nullable InventoryComponent.Armor armorComp,
            @Nullable InventoryComponent.Storage storageComp,
            @Nullable InventoryComponent.Hotbar hotbarComp,
            @Nullable InventoryComponent.Backpack backpackComp) {

        return new ItemContainer[]{
                armorComp != null ? armorComp.getInventory() : null,
                storageComp != null ? storageComp.getInventory() : null,
                hotbarComp != null ? hotbarComp.getInventory() : null,
                backpackComp != null ? backpackComp.getInventory() : null
        };
    }
}