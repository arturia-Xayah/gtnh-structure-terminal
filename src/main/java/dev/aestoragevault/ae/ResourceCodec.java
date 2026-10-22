package dev.aestoragevault.ae;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import dev.aestoragevault.core.Resource;

public final class ResourceCodec {

    private ResourceCodec() {}

    public static Resource encode(IAEStack<?> stack) throws IOException {
        NBTTagCompound tag = new NBTTagCompound();
        String channel;
        int perByte;
        if (stack instanceof IAEItemStack) {
            channel = "items";
            perByte = 8;
            ItemStack item = ((IAEItemStack) stack).getItemStack();
            String name = Item.itemRegistry.getNameForObject(item.getItem());
            if (name == null) throw new IOException("Unregistered item");
            tag.setString("Registry", name);
            tag.setShort("Damage", (short) item.getItemDamage());
            if (item.hasTagCompound()) tag.setTag(
                "Tag",
                item.getTagCompound()
                    .copy());
        } else if (stack instanceof IAEFluidStack) {
            channel = "fluids";
            perByte = 2048;
            FluidStack fluid = ((IAEFluidStack) stack).getFluidStack();
            fluid.amount = 1;
            fluid.writeToNBT(tag);
        } else throw new IOException("Unsupported cell channel; source retained");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(data));
        ByteArrayOutputStream key = new ByteArrayOutputStream();
        canonical(tag, new DataOutputStream(key));
        return new Resource(channel, perByte, key.toByteArray(), data.toByteArray());
    }

    public static IAEStack<?> decode(Resource resource) throws IOException {
        NBTTagCompound tag = CompressedStreamTools
            .read(new DataInputStream(new ByteArrayInputStream(resource.payload())));
        IAEStack<?> result;
        if (resource.channel.equals("items")) {
            Item item = (Item) Item.itemRegistry.getObject(tag.getString("Registry"));
            if (item == null) throw new IOException("Missing item " + tag.getString("Registry"));
            ItemStack stack = new ItemStack(item, 1, tag.getShort("Damage"));
            if (tag.hasKey("Tag")) stack.setTagCompound(tag.getCompoundTag("Tag"));
            result = AEItemStack.create(stack);
        } else if (resource.channel.equals("fluids")) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag);
            if (fluid == null) throw new IOException("Missing fluid " + tag.getString("FluidName"));
            result = AEFluidStack.create(fluid);
        } else throw new IOException("Unsupported stored channel " + resource.channel);
        if (result == null) throw new IOException("Invalid external resource");
        return result;
    }

    // Key identity ignores compound insertion order but preserves every nested value and NBT type.
    private static void canonical(NBTBase tag, DataOutputStream out) throws IOException {
        out.writeByte(tag.getId());
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            List<String> keys = new ArrayList<>();
            for (Object key : compound.func_150296_c()) keys.add((String) key);
            Collections.sort(keys);
            out.writeInt(keys.size());
            for (String key : keys) {
                out.writeUTF(key);
                canonical(compound.getTag(key), out);
            }
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            out.writeInt(list.tagCount());
            NBTTagList copy = (NBTTagList) list.copy();
            // 1.7.10 has no generic list accessor; remove from the end of a copy in O(n).
            for (int i = copy.tagCount() - 1; i >= 0; i--) canonical(copy.removeTag(i), out);
        } else if (tag instanceof NBTTagByteArray) {
            byte[] bytes = ((NBTTagByteArray) tag).func_150292_c();
            out.writeInt(bytes.length);
            out.write(bytes);
        } else if (tag instanceof NBTTagIntArray) {
            int[] values = ((NBTTagIntArray) tag).func_150302_c();
            out.writeInt(values.length);
            for (int value : values) out.writeInt(value);
        } else out.writeUTF(tag.toString());
    }
}
