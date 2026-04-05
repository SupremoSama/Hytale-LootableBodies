package com.supremosan.lootablebodies.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
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
    private static final String BODY_DEATH_ROLE = "Body_Death_Entity_Role";
    private static final String BODY_LOGOUT_ROLE = "Body_Logout_Entity_Role";

    public static void spawnBody(
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            ItemStack[] storageItems,
            ItemStack[] hotbarItems,
            ItemStack[] backpackItems,
            ItemStack[] armorItems,
            BodySource source
    ) {
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            return;
        }

        PlayerSkinComponent playerSkinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (playerSkinComponent == null) {
            return;
        }

        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
        String ownerUuid = playerRefComponent != null ? playerRefComponent.getUuid().toString() : "";

        String skinSerialized = serializePlayerSkin(playerSkinComponent);

        String roleName = source == BodySource.DEATH ? BODY_DEATH_ROLE : BODY_LOGOUT_ROLE;
        int roleIndex = NPCPlugin.get().getIndex(roleName);
        if (roleIndex < 0) {
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

        List<ItemStack> merged = mergeStacks(allItems);
        short capacity = (short) Math.max(merged.size(), 1);
        SimpleItemContainer storageContainer = new SimpleItemContainer(capacity);
        for (short i = 0; i < merged.size(); ++i) {
            storageContainer.setItemStackForSlot(i, merged.get(i));
        }

        newEntityStore.putComponent(newEntityRef, InventoryComponent.Storage.getComponentType(), new InventoryComponent.Storage(storageContainer));

        BodyComponent bodyComponent = new BodyComponent(skinSerialized, ownerUuid, source.name());
        bodyComponent.setStorageItems(storageItems);
        bodyComponent.setHotbarItems(hotbarItems);
        bodyComponent.setBackpackItems(backpackItems);
        bodyComponent.setArmorItems(armorItems);
        newEntityStore.putComponent(newEntityRef, LootableBodies.bodyComponentType, bodyComponent);
    }

    private static String serializePlayerSkin(PlayerSkinComponent skinComponent) {
        if (skinComponent == null) return "";
        var skin = skinComponent.getPlayerSkin();
        return String.join(";",
                nullToEmpty(skin.bodyCharacteristic),
                nullToEmpty(skin.ears),
                nullToEmpty(skin.skinFeature),
                nullToEmpty(skin.eyes),
                nullToEmpty(skin.eyebrows),
                nullToEmpty(skin.gloves),
                nullToEmpty(skin.overpants),
                nullToEmpty(skin.pants),
                nullToEmpty(skin.shoes),
                nullToEmpty(skin.cape),
                nullToEmpty(skin.earAccessory),
                nullToEmpty(skin.face),
                nullToEmpty(skin.faceAccessory),
                nullToEmpty(skin.facialHair),
                nullToEmpty(skin.haircut),
                nullToEmpty(skin.headAccessory),
                nullToEmpty(skin.mouth),
                nullToEmpty(skin.overtop),
                nullToEmpty(skin.undertop),
                nullToEmpty(skin.underwear)
        );
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    public static void syncBodyToPlayer(UUID uuid, Store<EntityStore> playerStore, Ref<EntityStore> playerRef) {
        if (uuid == null || playerStore == null || playerRef == null) {
            return;
        }

        Ref<EntityStore> bodyRef = findBodyRefByPlayer(uuid, BodySource.LOGOUT, playerStore);
        if (bodyRef == null || !bodyRef.isValid()) {
            return;
        }

        Store<EntityStore> bodyStore = bodyRef.getStore();

        BodyComponent bodyComponent = bodyStore.getComponent(bodyRef, LootableBodies.bodyComponentType);
        if (bodyComponent == null) {
            forceRemoveBody(bodyRef, bodyStore);
            return;
        }

        InventoryComponent.Storage bodyStorageComp = bodyStore.getComponent(bodyRef, InventoryComponent.Storage.getComponentType());
        ItemContainer bodyLive = bodyStorageComp != null ? bodyStorageComp.getInventory() : null;

        InventoryComponent.Storage playerStorageComp = playerStore.getComponent(playerRef, InventoryComponent.Storage.getComponentType());
        InventoryComponent.Hotbar playerHotbarComp = playerStore.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Backpack playerBackpackComp = playerStore.getComponent(playerRef, InventoryComponent.Backpack.getComponentType());
        InventoryComponent.Armor playerArmorComp = playerStore.getComponent(playerRef, InventoryComponent.Armor.getComponentType());

        ItemContainer playerStorage = playerStorageComp != null ? playerStorageComp.getInventory() : null;
        ItemContainer playerHotbar = playerHotbarComp != null ? playerHotbarComp.getInventory() : null;
        ItemContainer playerBackpack = playerBackpackComp != null ? playerBackpackComp.getInventory() : null;
        ItemContainer playerArmor = playerArmorComp != null ? playerArmorComp.getInventory() : null;

        clearContainer(playerStorage);
        clearContainer(playerHotbar);
        clearContainer(playerBackpack);
        clearContainer(playerArmor);

        restoreFromLiveBody(bodyComponent.getStorageItems(), bodyLive, playerStorage);
        restoreFromLiveBody(bodyComponent.getHotbarItems(), bodyLive, playerHotbar);
        restoreFromLiveBody(bodyComponent.getBackpackItems(), bodyLive, playerBackpack);
        restoreFromLiveBody(bodyComponent.getArmorItems(), bodyLive, playerArmor);

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

    private static void restoreFromLiveBody(ItemStack[] snapshot, ItemContainer bodyLive, ItemContainer playerTarget) {
        if (snapshot == null || playerTarget == null || bodyLive == null) return;
        for (short slot = 0; slot < snapshot.length && slot < playerTarget.getCapacity(); ++slot) {
            ItemStack original = snapshot[slot];
            if (ItemStack.isEmpty(original)) continue;

            ItemStack remaining = consumeFromLive(original, bodyLive);
            if (!ItemStack.isEmpty(remaining)) {
                playerTarget.setItemStackForSlot(slot, remaining);
            }
        }
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

    private static void addNonEmpty(ItemStack[] items, List<ItemStack> target) {
        if (items == null) return;
        for (ItemStack s : items) {
            if (!ItemStack.isEmpty(s)) target.add(s);
        }
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