package com.supremosan.lootablebodies.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
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
    private static final String BODY_ROLE = "Body_Entity_Role";

    public static void spawnBody(Store<EntityStore> store, Ref<EntityStore> ref, UUID uuid, List<ItemStack> stacks, ItemStack[] armor, BodySource source) {
        LOGGER.atInfo().log("[BodyManager] spawnBody called, stacks=%s, source=%s", stacks.size(), source);

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

        int roleIndex = NPCPlugin.get().getIndex(BODY_ROLE);
        if (roleIndex < 0) {
            LOGGER.atInfo().log("[BodyManager] Role not found: %s", BODY_ROLE);
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

        List<ItemStack> merged = mergeStacks(stacks);
        short capacity = (short) Math.max(merged.size(), 1);
        SimpleItemContainer storageContainer = new SimpleItemContainer(capacity);
        for (short i = 0; i < merged.size(); ++i) {
            storageContainer.setItemStackForSlot(i, merged.get(i));
        }
        newEntityStore.putComponent(newEntityRef, InventoryComponent.Storage.getComponentType(), new InventoryComponent.Storage(storageContainer));

        InventoryComponent.Armor npcArmorComp = newEntityStore.getComponent(newEntityRef, InventoryComponent.Armor.getComponentType());
        if (npcArmorComp != null && armor != null) {
            ItemContainer armorEntityInventory = npcArmorComp.getInventory();
            for (int i = 0; i < armor.length && i < armorEntityInventory.getCapacity(); ++i) {
                if (!ItemStack.isEmpty(armor[i])) {
                    armorEntityInventory.setItemStackForSlot((short) i, armor[i]);
                }
            }
        }

        newEntityStore.addComponent(
                newEntityRef,
                BodyComponent.getComponentType(),
                new BodyComponent(playerSkinComponent, uuid, source)
        );

        String animationId = source == BodySource.DEATH ? "Death" : "Sleep";
        AnimationUtils.playAnimation(newEntityRef, AnimationSlot.Status, animationId, newEntityStore);
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

        List<ItemStack> bodyItems = new ArrayList<>();

        InventoryComponent.Storage bodyStorageComp = bodyStore.getComponent(bodyRef, InventoryComponent.Storage.getComponentType());
        if (bodyStorageComp != null) {
            ItemContainer bodyInventory = bodyStorageComp.getInventory();
            for (short i = 0; i < bodyInventory.getCapacity(); ++i) {
                ItemStack stack = bodyInventory.getItemStack(i);
                if (!ItemStack.isEmpty(stack)) {
                    bodyItems.add(stack);
                }
            }
        }

        InventoryComponent.Armor bodyArmorComp = bodyStore.getComponent(bodyRef, InventoryComponent.Armor.getComponentType());
        if (bodyArmorComp != null) {
            ItemContainer bodyArmorInv = bodyArmorComp.getInventory();
            for (short i = 0; i < bodyArmorInv.getCapacity(); ++i) {
                ItemStack stack = bodyArmorInv.getItemStack(i);
                if (!ItemStack.isEmpty(stack)) {
                    bodyItems.add(stack);
                }
            }
        }

        LOGGER.atInfo().log("[BodyManager] syncBodyToPlayer: restoring %s items to player %s", bodyItems.size(), uuid);

        InventoryComponent.Storage playerStorageComp = playerStore.getComponent(playerRef, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar playerHotbarComp = playerStore.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack playerBackpackComp = playerStore.getComponent(playerRef, InventoryComponent.Backpack.getComponentType());
        InventoryComponent.Armor playerArmorComp = playerStore.getComponent(playerRef, InventoryComponent.Armor.getComponentType());

        ItemContainer[] playerContainers = new ItemContainer[]{
                playerStorageComp != null ? playerStorageComp.getInventory() : null,
                playerHotbarComp != null ? playerHotbarComp.getInventory() : null,
                playerBackpackComp != null ? playerBackpackComp.getInventory() : null,
                playerArmorComp != null ? playerArmorComp.getInventory() : null
        };

        for (ItemContainer container : playerContainers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); ++slot) {
                if (!ItemStack.isEmpty(container.getItemStack(slot))) {
                    container.removeItemStackFromSlot(slot);
                }
            }
        }

        for (ItemStack incoming : bodyItems) {
            if (ItemStack.isEmpty(incoming)) continue;

            ItemStack remaining = incoming;

            outer:
            for (ItemContainer container : playerContainers) {
                if (container == null) continue;

                for (short slot = 0; slot < container.getCapacity(); ++slot) {
                    if (ItemStack.isEmpty(remaining)) break outer;

                    ItemStack existing = container.getItemStack(slot);

                    if (ItemStack.isEmpty(existing)) {
                        container.setItemStackForSlot(slot, remaining);
                        remaining = ItemStack.EMPTY;
                    } else if (existing.isStackableWith(remaining)) {
                        int maxStack = existing.getItem().getMaxStack();
                        int combined = existing.getQuantity() + remaining.getQuantity();
                        if (combined <= maxStack) {
                            container.setItemStackForSlot(slot, existing.withQuantity(combined));
                            remaining = ItemStack.EMPTY;
                        } else {
                            int added = maxStack - existing.getQuantity();
                            container.setItemStackForSlot(slot, existing.withQuantity(maxStack));
                            remaining = remaining.withQuantity(remaining.getQuantity() - added);
                        }
                    }
                }
            }

            if (!ItemStack.isEmpty(remaining)) {
                LOGGER.atInfo().log("[BodyManager] syncBodyToPlayer: inventory full, could not restore item %s", remaining);
            }
        }

        forceRemoveBody(bodyRef, bodyStore);
    }

    public static void forceRemoveBody(Ref<EntityStore> bodyRef, Store<EntityStore> bodyStore) {
        if (bodyRef == null || bodyStore == null || !bodyRef.isValid()) {
            return;
        }

        NPCEntity npcEntity = bodyStore.getComponent(bodyRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity == null) {
            return;
        }

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