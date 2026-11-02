package dev.aestoragevault.world;

import java.util.Map;
import java.util.UUID;

/** Delivery receipts are saved in the same native tile NBT as the returned item stacks. */
public interface ReturnBus {

    UUID vaultBusId();

    Map<UUID, UUID> vaultDeliveries();
}
