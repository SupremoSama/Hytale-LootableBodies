package com.supremosan.lootablebodies.actions;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ValidatedWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

/**
 * A corpse container window that closes when the viewer walks away from the body.
 */
public class BodyWindow extends ContainerWindow implements ValidatedWindow {

    private static final double MAX_DISTANCE = 7.0D;
    private static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;

    private final Vector3d position;

    public BodyWindow(@Nonnull ItemContainer itemContainer, @Nonnull Vector3d position) {
        super(itemContainer);
        this.position = new Vector3d(position);
    }

    @Override
    public boolean validate(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> store) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return false;
        return transform.getPosition().distanceSquared(position) <= MAX_DISTANCE_SQR;
    }
}
