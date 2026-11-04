package dev.aestoragevault;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent.Action;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping;

public class RetiredCasingTest {

    @Test
    public void removalAppliesOnlyToRetiredBlockAndItem() {
        ListMultimap<String, MissingMapping> mappings = ArrayListMultimap.create();
        MissingMapping block = new MissingMapping("\u0001aestoragevault:casing", 1000);
        MissingMapping item = new MissingMapping("\u0002aestoragevault:casing", 1000);
        MissingMapping controller = new MissingMapping("\u0001aestoragevault:controller", 1001);
        MissingMapping other = new MissingMapping("\u0001other:casing", 1002);
        mappings.put(VaultMod.ID, block);
        mappings.put(VaultMod.ID, item);
        mappings.put(VaultMod.ID, controller);
        mappings.put("other", other);
        FMLMissingMappingsEvent event = new FMLMissingMappingsEvent(mappings);
        ModMetadata metadata = new ModMetadata();
        metadata.modId = VaultMod.ID;
        event.applyModContainer(new DummyModContainer(metadata));
        new VaultMod().missingMappings(event);
        assertEquals(Action.IGNORE, block.getAction());
        assertEquals(Action.IGNORE, item.getAction());
        assertEquals(Action.DEFAULT, controller.getAction());
        assertEquals(Action.DEFAULT, other.getAction());
    }
}
