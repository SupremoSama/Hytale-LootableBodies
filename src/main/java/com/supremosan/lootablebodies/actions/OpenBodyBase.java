package com.supremosan.lootablebodies.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.supremosan.lootablebodies.components.BodyComponent;
import com.supremosan.lootablebodies.components.BodySource;

import javax.annotation.Nonnull;
import java.util.Objects;

public class OpenBodyBase extends ActionBase {

    public OpenBodyBase(@Nonnull BuilderActionBase builderActionBase) {
        super(builderActionBase);
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        return super.canExecute(ref, role, sensorInfo, dt, store) && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        Ref<EntityStore> playerReference = role.getStateSupport().getInteractionIterationTarget();
        if (playerReference == null) return false;

        PlayerRef playerRefComponent = store.getComponent(playerReference, PlayerRef.getComponentType());
        if (playerRefComponent == null) return false;

        Player playerComponent = store.getComponent(playerReference, Player.getComponentType());
        if (playerComponent == null) return false;

        Store<EntityStore> npcStore = ref.getStore();

        InventoryComponent.Storage storageComp = npcStore.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (storageComp == null) return false;

        ItemContainer storage = storageComp.getInventory();

        NPCEntity npcEntity = npcStore.getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity == null) return false;

        BodyComponent bodyComponent = npcStore.getComponent(ref, BodyComponent.getComponentType());
        BodySource bodySource = bodyComponent != null ? bodyComponent.bodySource : BodySource.DEATH;

        ContainerWindow containerWindow = new ContainerWindow(storage);
        containerWindow.registerCloseEvent((_) -> {
            if (storage.isEmpty() && bodySource == BodySource.DEATH) {
                npcEntity.remove();
            }
        });

        playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Inventory, true, containerWindow);
        return true;
    }
}