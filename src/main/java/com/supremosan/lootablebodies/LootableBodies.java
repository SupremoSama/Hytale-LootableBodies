package com.supremosan.lootablebodies;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.supremosan.lootablebodies.actions.OpenBodyAction;
import com.supremosan.lootablebodies.components.BodyComponent;
import com.supremosan.lootablebodies.events.CreateBodyOnDeathEvent;
import com.supremosan.lootablebodies.system.BodyManager;
import com.supremosan.lootablebodies.system.BodySkinReapplySystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

public class LootableBodies extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static ComponentType<EntityStore, BodyComponent> bodyComponentType;

    public LootableBodies(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        NPCPlugin.get().registerCoreComponentType("OpenBody", OpenBodyAction::new);

        bodyComponentType = this.getEntityStoreRegistry().registerComponent(
                BodyComponent.class, "BodyComponent", BodyComponent.CODEC);

        this.getEntityStoreRegistry().registerSystem(new CreateBodyOnDeathEvent());
        this.getEntityStoreRegistry().registerSystem(new BodySkinReapplySystem());

        Field modelReferenceScaleField;
        try {
            modelReferenceScaleField = Model.ModelReference.class.getDeclaredField("scale");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        modelReferenceScaleField.setAccessible(true);
        try {
            modelReferenceScaleField.set(Model.ModelReference.DEFAULT_PLAYER_MODEL, 1.0F);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID uuid = playerRef.getUuid();
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) return;

            Store<EntityStore> store = ref.getStore();
            World world = ((EntityStore) store.getExternalData()).getWorld();
            world.execute(() -> {
                InventoryComponent.Armor armorComp = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
                InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
                InventoryComponent.Backpack backpackComp = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

                if (transform == null) return;

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
                        if (!ItemStack.isEmpty(stack)) stacks.add(stack);
                    }
                }

                ItemContainer armorInventory = armorComp != null ? armorComp.getInventory() : null;
                ItemStack[] armorStacks = new ItemStack[armorInventory != null ? armorInventory.getCapacity() : 0];
                if (armorInventory != null) {
                    for (short i = 0; i < armorStacks.length; ++i) {
                        armorStacks[i] = armorInventory.getItemStack(i);
                    }
                }

                if (stacks.isEmpty()) return;

                LOGGER.atInfo().log("[LootableBodies] Saved body for %s with %s items", uuid, stacks.size());
                BodyManager.spawnBody(store, ref, uuid, stacks, armorStacks);
            });
        });

        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID uuid = playerRef.getUuid();

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) return;

            Store<EntityStore> store = ref.getStore();
            if (!BodyManager.hasBody(uuid)) {
                LOGGER.atInfo().log("[LootableBodies] No active body for %s", uuid);
                return;
            }

            World world = ((EntityStore) store.getExternalData()).getWorld();
            world.execute(() -> {
                LOGGER.atInfo().log("[LootableBodies] Found active body for %s, restoring items", uuid);
                BodyManager.restoreBodyToPlayer(uuid, store, ref);
            });
        });

        LOGGER.atInfo().log("[LootableBodies] Setup complete");
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("[LootableBodies] Ready");
    }
}