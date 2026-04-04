package com.supremosan.lootablebodies.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.supremosan.lootablebodies.LootableBodies;
import com.supremosan.lootablebodies.components.BodyComponent;
import com.supremosan.lootablebodies.components.BodySource;
import it.unimi.dsi.fastutil.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class BodyManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String BODY_DEATH_ROLE = "Body_Death_Entity_Role";
    private static final String BODY_LOGOUT_ROLE = "Body_Logout_Entity_Role";

    public static void spawnBody(
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            UUID uuid,
            ItemStack[] storageItems,
            ItemStack[] hotbarItems,
            ItemStack[] backpackItems,
            ItemStack[] armorItems,
            BodySource source
    ) {
        LOGGER.atInfo().log("[BodyManager] spawnBody called, source=%s, uuid=%s", source, uuid);

        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            LOGGER.atInfo().log("[BodyManager] TransformComponent is null, aborting");
            return;
        }

        PlayerSkinComponent playerSkinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (playerSkinComponent == null) {
            LOGGER.atInfo().log("[BodyManager] PlayerSkinComponent is null, aborting");
            return;
        }

        String roleName = source == BodySource.DEATH ? BODY_DEATH_ROLE : BODY_LOGOUT_ROLE;
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
            LOGGER.atInfo().log("[BodyManager] Role not found: %s", roleName);
            return;
        }

        Model newModel = Model.createScaledModel(Objects.requireNonNull(ModelAsset.getAssetMap().getAsset("Player")), 1.0F);
        Vector3d position = transformComponent.getPosition();

        Pair<Ref<EntityStore>, NPCEntity> pair = NPCPlugin.get().spawnEntity(
                store,
                roleIndex,
                position,
                transformComponent.getRotation(),
                newModel,
                null
        );

        if (pair == null) {
            LOGGER.atInfo().log("[BodyManager] spawnEntity returned null");
            return;
        }

        Store<EntityStore> newEntityStore = pair.first().getStore();
        Ref<EntityStore> newEntityRef = pair.first();

        newEntityStore.putComponent(newEntityRef, ModelComponent.getComponentType(), new ModelComponent(newModel));

        PlayerSkinComponent skinComp = new PlayerSkinComponent(playerSkinComponent.getPlayerSkin().clone());
        newEntityStore.putComponent(newEntityRef, PlayerSkinComponent.getComponentType(), skinComp);
        skinComp.setNetworkOutdated();

        List<ItemStack> allItems = new ArrayList<>();
        addNonEmpty(storageItems, allItems);
        addNonEmpty(hotbarItems, allItems);
        addNonEmpty(backpackItems, allItems);
        addNonEmpty(armorItems, allItems);

        LOGGER.atInfo().log("[BodyManager] spawnBody: merged item count=%d", allItems.size());
        for (int i = 0; i < allItems.size(); i++) {
            LOGGER.atInfo().log("[BodyManager]   allItems[%d] = %s x%d", i, allItems.get(i).getItem(), allItems.get(i).getQuantity());
        }

        List<ItemStack> merged = mergeStacks(allItems);
        short capacity = (short) Math.max(merged.size(), 1);
        SimpleItemContainer storageContainer = new SimpleItemContainer(capacity);
        for (short i = 0; i < merged.size(); ++i) {
            storageContainer.setItemStackForSlot(i, merged.get(i));
        }

        LOGGER.atInfo().log("[BodyManager] spawnBody: body storage capacity=%d", capacity);
        for (short i = 0; i < storageContainer.getCapacity(); i++) {
            ItemStack s = storageContainer.getItemStack(i);
            if (!ItemStack.isEmpty(s)) {
                LOGGER.atInfo().log("[BodyManager]   storage[%d] = %s x%d", i, s.getItem(), s.getQuantity());
            }
        }

        newEntityStore.putComponent(newEntityRef, InventoryComponent.Storage.getComponentType(), new InventoryComponent.Storage(storageContainer));

        BodyComponent bodyComponent = new BodyComponent(playerSkinComponent, uuid, source, storageItems, hotbarItems, backpackItems, armorItems);
        LOGGER.atInfo().log("[BodyManager] BodyComponent before put: owner=%s source=%s", bodyComponent.ownerUuidSerialized, bodyComponent.bodySource);
        newEntityStore.putComponent(newEntityRef, LootableBodies.bodyComponentType, bodyComponent);

        BodyComponent readBack = newEntityStore.getComponent(newEntityRef, LootableBodies.bodyComponentType);
        LOGGER.atInfo().log("[BodyManager] BodyComponent readBack: owner=%s source=%s",
                readBack != null ? readBack.ownerUuidSerialized : "NULL",
                readBack != null ? readBack.bodySource : "NULL");

        LOGGER.atInfo().log("[BodyManager] snapshot counts: storage=%d hotbar=%d backpack=%d armor=%d",
                countNonEmpty(storageItems), countNonEmpty(hotbarItems), countNonEmpty(backpackItems), countNonEmpty(armorItems));
    }

    public static void syncBodyToPlayer(UUID uuid, Store<EntityStore> playerStore, Ref<EntityStore> playerRef) {
        if (uuid == null || playerStore == null || playerRef == null) {
            return;
        }

        Ref<EntityStore> bodyRef = findBodyRefByPlayer(uuid, BodySource.LOGOUT, playerStore);
        if (bodyRef == null || !bodyRef.isValid()) {
            LOGGER.atInfo().log("[BodyManager] syncBodyToPlayer: no LOGOUT body found for %s", uuid);
            return;
        }

        Store<EntityStore> bodyStore = bodyRef.getStore();

        BodyComponent bodyComponent = bodyStore.getComponent(bodyRef, LootableBodies.bodyComponentType);
        if (bodyComponent == null) {
            LOGGER.atInfo().log("[BodyManager] syncBodyToPlayer: BodyComponent is null for %s", uuid);
            forceRemoveBody(bodyRef, bodyStore);
            return;
        }

        InventoryComponent.Storage bodyStorageComp = bodyStore.getComponent(bodyRef, InventoryComponent.Storage.getComponentType());
        ItemContainer bodyLive = bodyStorageComp != null ? bodyStorageComp.getInventory() : null;

        LOGGER.atInfo().log("[BodyManager] syncBodyToPlayer: bodyLive capacity=%s",
                bodyLive != null ? bodyLive.getCapacity() : "NULL");
        if (bodyLive != null) {
            for (short i = 0; i < bodyLive.getCapacity(); i++) {
                ItemStack s = bodyLive.getItemStack(i);
                if (!ItemStack.isEmpty(s)) {
                    LOGGER.atInfo().log("[BodyManager]   bodyLive[%d] = %s x%d", i, s.getItem(), s.getQuantity());
                }
            }
        }

        LOGGER.atInfo().log("[BodyManager] syncBodyToPlayer: snapshot counts: storage=%d hotbar=%d backpack=%d armor=%d",
                countNonEmpty(bodyComponent.getStorageItems()),
                countNonEmpty(bodyComponent.getHotbarItems()),
                countNonEmpty(bodyComponent.getBackpackItems()),
                countNonEmpty(bodyComponent.getArmorItems()));

        InventoryComponent.Storage playerStorageComp = playerStore.getComponent(playerRef, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar playerHotbarComp = playerStore.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack playerBackpackComp = playerStore.getComponent(playerRef, InventoryComponent.Backpack.getComponentType());
        InventoryComponent.Armor playerArmorComp = playerStore.getComponent(playerRef, InventoryComponent.Armor.getComponentType());

        ItemContainer playerStorage = playerStorageComp != null ? playerStorageComp.getInventory() : null;
        ItemContainer playerHotbar = playerHotbarComp != null ? playerHotbarComp.getInventory() : null;
        ItemContainer playerBackpack = playerBackpackComp != null ? playerBackpackComp.getInventory() : null;
        ItemContainer playerArmor = playerArmorComp != null ? playerArmorComp.getInventory() : null;

        LOGGER.atInfo().log("[BodyManager] player containers: storage=%s hotbar=%s backpack=%s armor=%s",
                playerStorage != null ? "cap=" + playerStorage.getCapacity() : "NULL",
                playerHotbar != null ? "cap=" + playerHotbar.getCapacity() : "NULL",
                playerBackpack != null ? "cap=" + playerBackpack.getCapacity() : "NULL",
                playerArmor != null ? "cap=" + playerArmor.getCapacity() : "NULL");

        clearContainer(playerStorage);
        clearContainer(playerHotbar);
        clearContainer(playerBackpack);
        clearContainer(playerArmor);

        int restoredStorage = restoreFromLiveBody(bodyComponent.getStorageItems(), bodyLive, playerStorage);
        int restoredHotbar = restoreFromLiveBody(bodyComponent.getHotbarItems(), bodyLive, playerHotbar);
        int restoredBackpack = restoreFromLiveBody(bodyComponent.getBackpackItems(), bodyLive, playerBackpack);
        int restoredArmor = restoreFromLiveBody(bodyComponent.getArmorItems(), bodyLive, playerArmor);

        LOGGER.atInfo().log("[BodyManager] syncBodyToPlayer: restored storage=%d hotbar=%d backpack=%d armor=%d for %s",
                restoredStorage, restoredHotbar, restoredBackpack, restoredArmor, uuid);

        forceRemoveBody(bodyRef, bodyStore);
    }

    private static void clearContainer(ItemContainer container) {
        if (container == null) return;
        for (short slot = 0; slot < container.getCapacity(); ++slot) {
            if (!ItemStack.isEmpty(container.getItemStack(slot))) {
                container.removeItemStackFromSlot(slot);
            }
        }
    }

    private static int restoreFromLiveBody(ItemStack[] snapshot, ItemContainer bodyLive, ItemContainer playerTarget) {
        if (snapshot == null || playerTarget == null || bodyLive == null) return 0;
        int count = 0;

        for (short slot = 0; slot < snapshot.length && slot < playerTarget.getCapacity(); ++slot) {
            ItemStack original = snapshot[slot];
            if (ItemStack.isEmpty(original)) continue;

            ItemStack remaining = consumeFromLive(original, bodyLive);
            if (!ItemStack.isEmpty(remaining)) {
                playerTarget.setItemStackForSlot(slot, remaining);
                LOGGER.atInfo().log("[BodyManager]   restore slot=%d item=%s x%d", slot, remaining.getItem(), remaining.getQuantity());
                count++;
            } else {
                LOGGER.atInfo().log("[BodyManager]   restore slot=%d item=%s - NOT FOUND IN BODY (stolen or missing)", slot, original.getItem());
            }
        }

        return count;
    }

    private static ItemStack consumeFromLive(ItemStack original, ItemContainer bodyLive) {
        for (short slot = 0; slot < bodyLive.getCapacity(); ++slot) {
            ItemStack live = bodyLive.getItemStack(slot);
            if (ItemStack.isEmpty(live) || !live.isStackableWith(original)) continue;

            int take = Math.min(live.getQuantity(), original.getQuantity());
            int leftover = live.getQuantity() - take;

            if (leftover > 0) {
                bodyLive.setItemStackForSlot(slot, live.withQuantity(leftover));
            } else {
                bodyLive.removeItemStackFromSlot(slot);
            }

            return live.withQuantity(take);
        }

        return ItemStack.EMPTY;
    }

    public static void forceRemoveBody(Ref<EntityStore> bodyRef, Store<EntityStore> bodyStore) {
        if (bodyRef == null || bodyStore == null || !bodyRef.isValid()) {
            LOGGER.atInfo().log("[BodyManager] forceRemoveBody: invalid ref or store");
            return;
        }

        NPCEntity npcEntity = bodyStore.getComponent(bodyRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity == null) {
            LOGGER.atInfo().log("[BodyManager] forceRemoveBody: NPCEntity is null");
            return;
        }

        LOGGER.atInfo().log("[BodyManager] forceRemoveBody: removing body entity");
        npcEntity.remove();
    }

    public static boolean hasBody(UUID uuid, Store<EntityStore> store) {
        return findBodyRefByPlayer(uuid, null, store) != null;
    }

    private static Ref<EntityStore> findBodyRefByPlayer(UUID uuid, BodySource requiredSource, Store<EntityStore> store) {
        if (uuid == null || store == null) {
            return null;
        }

        final Ref<EntityStore>[] found = new Ref[1];
        store.forEachChunk(LootableBodies.bodyComponentType, (archetypeChunk, commandBuffer) -> {
            if (found[0] != null) {
                return;
            }

            for (int index = 0; index < archetypeChunk.size(); ++index) {
                BodyComponent bodyComponent = archetypeChunk.getComponent(index, LootableBodies.bodyComponentType);
                if (bodyComponent == null) {
                    continue;
                }

                LOGGER.atInfo().log("[BodyManager] scanning body: owner='%s' source=%s", bodyComponent.ownerUuidSerialized, bodyComponent.bodySource);

                if (bodyComponent.ownerUuidSerialized.isEmpty()) {
                    continue;
                }

                if (!uuid.toString().equals(bodyComponent.ownerUuidSerialized)) {
                    continue;
                }

                if (requiredSource != null && bodyComponent.bodySource != requiredSource) {
                    continue;
                }

                found[0] = archetypeChunk.getReferenceTo(index);
                return;
            }
        });

        return found[0];
    }

    private static void addNonEmpty(ItemStack[] items, List<ItemStack> target) {
        if (items == null) return;
        for (ItemStack s : items) {
            if (!ItemStack.isEmpty(s)) target.add(s);
        }
    }

    private static int countNonEmpty(ItemStack[] items) {
        if (items == null) return 0;
        int count = 0;
        for (ItemStack s : items) {
            if (!ItemStack.isEmpty(s)) count++;
        }
        return count;
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack incoming : stacks) {
            if (incoming == null || ItemStack.isEmpty(incoming)) {
                continue;
            }

            boolean mergedIntoExisting = false;

            for (int i = 0; i < merged.size(); i++) {
                ItemStack existing = merged.get(i);

                if (!existing.isStackableWith(incoming)) {
                    continue;
                }

                int maxStackSize = existing.getItem().getMaxStack();
                int combined = existing.getQuantity() + incoming.getQuantity();

                if (combined <= maxStackSize) {
                    merged.set(i, existing.withQuantity(combined));
                    mergedIntoExisting = true;
                    break;
                }

                if (existing.getQuantity() < maxStackSize) {
                    int amountToFill = maxStackSize - existing.getQuantity();
                    merged.set(i, existing.withQuantity(maxStackSize));
                    incoming = incoming.withQuantity(incoming.getQuantity() - amountToFill);
                }
            }

            if (!mergedIntoExisting) {
                merged.add(incoming);
            }
        }

        return merged;
    }
}