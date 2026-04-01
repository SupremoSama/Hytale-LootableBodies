package com.supremosan.lootablebodies.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.function.consumer.TriConsumer;
import com.hypixel.hytale.logger.HytaleLogger;
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
import com.supremosan.lootablebodies.components.BodyComponent;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BodyManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String BODY_ROLE = "Body_Entity_Role";

    private static final Map<UUID, Ref<EntityStore>> bodyRefByPlayer = new ConcurrentHashMap<>();

    public static void spawnBody(Store<EntityStore> store, Ref<EntityStore> ref, UUID uuid, List<ItemStack> stacks, ItemStack[] armor) {
        LOGGER.atInfo().log("[BodyManager] spawnBody called, stacks=%s", stacks.size());

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

        LOGGER.atInfo().log("[BodyManager] Attempting spawnEntity with role=%s at pos=%s", BODY_ROLE, transformComponent.getPosition());

        Pair<Ref<EntityStore>, NPCEntity> pair = NPCPlugin.get().spawnEntity(
                store,
                roleIndex,
                transformComponent.getPosition(),
                transformComponent.getRotation(),
                newModel,
                (TriConsumer) null
        );

        if (pair == null) {
            LOGGER.atInfo().log("[BodyManager] spawnEntity returned null");
            return;
        }

        LOGGER.atInfo().log("[BodyManager] spawnEntity succeeded, setting up body entity");

        Store<EntityStore> newEntityStore = pair.first().getStore();
        Ref<EntityStore> newEntityRef = pair.first();

        if (uuid != null) {
            bodyRefByPlayer.put(uuid, newEntityRef);
            LOGGER.atInfo().log("[BodyManager] Registered body ref for player %s", uuid);
        }

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
        LOGGER.atInfo().log("[BodyManager] Storage capacity=%s, slots used=%s", capacity, merged.size());

        InventoryComponent.Armor npcArmorComp = newEntityStore.getComponent(newEntityRef, InventoryComponent.Armor.getComponentType());
        if (npcArmorComp != null) {
            ItemContainer armorEntityInventory = npcArmorComp.getInventory();
            for (int i = 0; i < armor.length && i < armorEntityInventory.getCapacity(); ++i) {
                if (!ItemStack.isEmpty(armor[i])) {
                    armorEntityInventory.setItemStackForSlot((short) i, armor[i]);
                }
            }
        } else {
            LOGGER.atInfo().log("[BodyManager] NPC has no Armor inventory component");
        }

        newEntityStore.addComponent(newEntityRef, BodyComponent.getComponentType(), new BodyComponent(playerSkinComponent));
        AnimationUtils.playAnimation(newEntityRef, AnimationSlot.Status, "Sleep", newEntityStore);

        LOGGER.atInfo().log("[BodyManager] Body entity setup complete");
    }

    public static void restoreBodyToPlayer(UUID uuid, Store<EntityStore> playerStore, Ref<EntityStore> playerRef) {
        Ref<EntityStore> bodyRef = bodyRefByPlayer.get(uuid);
        if (bodyRef == null) {
            LOGGER.atInfo().log("[BodyManager] No body ref found for player %s", uuid);
            return;
        }

        Store<EntityStore> bodyStore = bodyRef.getStore();
        if (bodyStore == null) {
            LOGGER.atInfo().log("[BodyManager] Body store is null for player %s, cleaning up", uuid);
            bodyRefByPlayer.remove(uuid);
            return;
        }

        InventoryComponent.Storage bodyStorageComp = bodyStore.getComponent(bodyRef, InventoryComponent.Storage.getComponentType());
        if (bodyStorageComp == null) {
            LOGGER.atInfo().log("[BodyManager] Body has no storage for player %s", uuid);
            removeBody(uuid);
            return;
        }

        ItemContainer bodyInventory = bodyStorageComp.getInventory();
        List<ItemStack> remaining = new ObjectArrayList<>();
        for (short i = 0; i < bodyInventory.getCapacity(); ++i) {
            ItemStack stack = bodyInventory.getItemStack(i);
            if (!ItemStack.isEmpty(stack)) remaining.add(stack);
        }

        if (remaining.isEmpty()) {
            LOGGER.atInfo().log("[BodyManager] Body already fully looted for player %s, removing", uuid);
            removeBody(uuid);
            return;
        }

        InventoryComponent.Storage playerStorageComp = playerStore.getComponent(playerRef, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar playerHotbarComp = playerStore.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack playerBackpackComp = playerStore.getComponent(playerRef, InventoryComponent.Backpack.getComponentType());

        ItemContainer[] playerContainers = new ItemContainer[]{
                playerStorageComp != null ? playerStorageComp.getInventory() : null,
                playerHotbarComp != null ? playerHotbarComp.getInventory() : null,
                playerBackpackComp != null ? playerBackpackComp.getInventory() : null
        };

        for (ItemStack stack : remaining) {
            boolean placed = false;
            for (ItemContainer container : playerContainers) {
                if (container == null) continue;
                for (short slot = 0; slot < container.getCapacity(); ++slot) {
                    ItemStack existing = container.getItemStack(slot);
                    if (ItemStack.isEmpty(existing)) {
                        container.setItemStackForSlot(slot, stack);
                        placed = true;
                        break;
                    }
                    if (existing.isStackableWith(stack)) {
                        int space = existing.getItem().getMaxStack() - existing.getQuantity();
                        if (space > 0) {
                            int toAdd = Math.min(space, stack.getQuantity());
                            container.setItemStackForSlot(slot, existing.withQuantity(existing.getQuantity() + toAdd));
                            int leftover = stack.getQuantity() - toAdd;
                            if (leftover > 0) {
                                stack = stack.withQuantity(leftover);
                            } else {
                                placed = true;
                                break;
                            }
                        }
                    }
                }
                if (placed) break;
            }
            if (!placed) {
                LOGGER.atInfo().log("[BodyManager] Could not place item %s back into player inventory (full?)", stack);
            }
        }

        LOGGER.atInfo().log("[BodyManager] Transferred %s items from body to player %s", remaining.size(), uuid);
        removeBody(uuid);
    }

    public static void removeBody(UUID uuid) {
        Ref<EntityStore> bodyRef = bodyRefByPlayer.remove(uuid);
        if (bodyRef == null) return;

        Store<EntityStore> bodyStore = bodyRef.getStore();
        if (bodyStore == null) return;

        NPCEntity npcEntity = bodyStore.getComponent(bodyRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            npcEntity.remove();
            LOGGER.atInfo().log("[BodyManager] Removed body NPC for player %s", uuid);
        }
    }

    public static boolean hasBody(UUID uuid) {
        return bodyRefByPlayer.containsKey(uuid);
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack incoming : stacks) {
            if (ItemStack.isEmpty(incoming)) continue;
            boolean fullyMerged = false;
            for (int i = 0; i < merged.size(); i++) {
                ItemStack existing = merged.get(i);
                if (!existing.isStackableWith(incoming)) continue;
                int maxStack = existing.getItem().getMaxStack();
                if (existing.getQuantity() >= maxStack) continue;
                int total = existing.getQuantity() + incoming.getQuantity();
                if (total <= maxStack) {
                    merged.set(i, existing.withQuantity(total));
                    fullyMerged = true;
                    break;
                } else {
                    merged.set(i, existing.withQuantity(maxStack));
                    incoming = incoming.withQuantity(total - maxStack);
                }
            }
            if (!fullyMerged) {
                merged.add(incoming);
            }
        }
        return merged;
    }

    public static void spawnBody(Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

        List<ItemStack> stacks = new ObjectArrayList<>();

        ItemContainer[] containers = new ItemContainer[]{
                storageComp != null ? storageComp.getInventory() : null,
                hotbarComp != null ? hotbarComp.getInventory() : null,
                backpackComp != null ? backpackComp.getInventory() : null
        };

        for (ItemContainer container : containers) {
            if (container == null) continue;
            for (short i = 0; i < container.getCapacity(); ++i) {
                ItemStack stack = container.getItemStack(i);
                if (stack != null) stacks.add(stack);
            }
        }

        ItemContainer armorInventory = armorComp != null ? armorComp.getInventory() : null;
        ItemStack[] armorStacks = new ItemStack[armorInventory != null ? armorInventory.getCapacity() : 0];
        if (armorInventory != null) {
            for (short i = 0; i < armorStacks.length; ++i) {
                armorStacks[i] = armorInventory.getItemStack(i);
            }
        }

        spawnBody(store, ref, null, stacks, armorStacks);
    }
}