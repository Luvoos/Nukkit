package cn.nukkit.entity;

import cn.nukkit.block.BlockID;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.PlayerEnderChestInventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSkull;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public abstract class EntityHumanType extends EntityCreature implements InventoryHolder {

    protected PlayerInventory inventory;
    protected PlayerEnderChestInventory enderChestInventory;
    protected PlayerOffhandInventory offhandInventory;

    public EntityHumanType(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public PlayerInventory getInventory() {
        return inventory;
    }

    public PlayerEnderChestInventory getEnderChestInventory() {
        return enderChestInventory;
    }

    public PlayerOffhandInventory getOffhandInventory() {
        return offhandInventory;
    }

    @Override
    protected void initEntity() {
        this.inventory = new PlayerInventory(this);
        this.offhandInventory = new PlayerOffhandInventory(this);

        if (this.namedTag.contains("Inventory") && this.namedTag.get("Inventory") instanceof ListTag) {
            ListTag<CompoundTag> inventoryList = this.namedTag.getList("Inventory", CompoundTag.class);
            for (CompoundTag item : inventoryList.getAll()) {
                int slot = item.getByte("Slot");
                if (slot >= 0 && slot < 9) {
                    // Old hotbar saving stuff, remove it (useless now)
                    inventoryList.remove(item);
                } else if (slot >= 100 && slot < 104) {
                    this.inventory.setItem(this.inventory.getSize() + slot - 100, NBTIO.getItemHelper(item));
                } else if (slot == -106) {
                    this.offhandInventory.setItem(0, NBTIO.getItemHelper(item));
                } else {
                    this.inventory.setItem(slot - 9, NBTIO.getItemHelper(item));
                }
            }
        }

        this.enderChestInventory = new PlayerEnderChestInventory(this);

        if (this.namedTag.contains("EnderItems") && this.namedTag.get("EnderItems") instanceof ListTag) {
            ListTag<CompoundTag> inventoryList = this.namedTag.getList("EnderItems", CompoundTag.class);
            for (CompoundTag item : inventoryList.getAll()) {
                this.enderChestInventory.setItem(item.getByte("Slot"), NBTIO.getItemHelper(item));
            }
        }

        super.initEntity();
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        ListTag<CompoundTag> inventoryTag = null;
        if (this.inventory != null) {
            inventoryTag = new ListTag<>("Inventory");
            this.namedTag.putList(inventoryTag);

            for (int slot = 0; slot < 9; ++slot) {
                inventoryTag.add(new CompoundTag()
                        .putByte("Count", 0)
                        .putShort("Damage", 0)
                        .putByte("Slot", slot)
                        .putByte("TrueSlot", -1)
                        .putShort("id", 0)
                );
            }

            int slotCount = 45; // SURVIVAL_SLOTS + 9
            for (int slot = 9; slot < slotCount; ++slot) {
                Item item = this.inventory.getItem(slot - 9);
                inventoryTag.add(NBTIO.putItemHelper(item, slot));
            }

            for (int slot = 100; slot < 104; ++slot) {
                Item item = this.inventory.getItem(this.inventory.getSize() + slot - 100);
                if (item != null && item.getId() != Item.AIR) {
                    inventoryTag.add(NBTIO.putItemHelper(item, slot));
                }
            }
        }

        if (this.offhandInventory != null) {
            Item item = this.offhandInventory.getItem(0);
            if (item.getId() != Item.AIR) {
                if (inventoryTag == null) {
                    inventoryTag = new ListTag<>("Inventory");
                    this.namedTag.putList(inventoryTag);
                }
                inventoryTag.add(NBTIO.putItemHelper(item, -106));
            }
        }

        this.namedTag.putList(new ListTag<CompoundTag>("EnderItems"));
        if (this.enderChestInventory != null) {
            for (int slot = 0; slot < 27; ++slot) {
                Item item = this.enderChestInventory.getItem(slot);
                if (item != null && item.getId() != Item.AIR) {
                    this.namedTag.getList("EnderItems", CompoundTag.class).add(NBTIO.putItemHelper(item, slot));
                }
            }
        }
    }

    @Override
    public Item[] getDrops() {
        if (this.inventory != null) {
            List<Item> drops = new ArrayList<>(this.inventory.getContents().values());
            drops.addAll(this.offhandInventory.getContents().values());
            return drops.toArray(new Item[0]);
        }
        return new Item[0];
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (closed || !this.isAlive()) {
            return false;
        }

        if (source.getCause() != DamageCause.VOID && source.getCause() != DamageCause.CUSTOM && source.getCause() != DamageCause.HUNGER) {
            int armorPoints = 0;
            int epf = 0;

            for (Item armor : inventory.getArmorContents()) {
                armorPoints += armor.getArmorPoints();
                epf += calculateEnchantmentProtectionFactor(armor, source);
            }

            //float originalDamage = source.getDamage();
            //float r = (source.getDamage(EntityDamageEvent.DamageModifier.ARMOR) - (originalDamage - originalDamage * (1 - Math.max(armorPoints / 5, armorPoints - originalDamage / 2) / 25)));
            //originalDamage += r;
            //epf = Math.min(20, epf);
            //source.setDamage(r, EntityDamageEvent.DamageModifier.ARMOR);
            //source.setDamage(source.getDamage(EntityDamageEvent.DamageModifier.ARMOR_ENCHANTMENTS) - (originalDamage - originalDamage * (1 - epf / 25f)), EntityDamageEvent.DamageModifier.ARMOR_ENCHANTMENTS);

            if (source.canBeReducedByArmor()) {
                source.setDamage(-source.getFinalDamage() * armorPoints * 0.04f, EntityDamageEvent.DamageModifier.ARMOR);
            }

            source.setDamage(-source.getFinalDamage() * Math.min(NukkitMath.ceilFloat(Math.min(epf, 25) * ((float) ThreadLocalRandom.current().nextInt(50, 100) / 100)), 20) * 0.04f,
                    EntityDamageEvent.DamageModifier.ARMOR_ENCHANTMENTS);

            //source.setDamage(-Math.min(this.getAbsorption(), source.getFinalDamage()), EntityDamageEvent.DamageModifier.ABSORPTION);

            if (super.attack(source)) {
                Entity damager = null;
                if (source instanceof EntityDamageByEntityEvent) {
                    damager = ((EntityDamageByEntityEvent) source).getDamager();
                }

                if (source.getCause() != DamageCause.VOID &&
                        source.getCause() != DamageCause.MAGIC &&
                        source.getCause() != DamageCause.HUNGER &&
                        source.getCause() != DamageCause.DROWNING &&
                        source.getCause() != DamageCause.SUFFOCATION &&
                        source.getCause() != DamageCause.SUICIDE &&
                        source.getCause() != DamageCause.FIRE_TICK &&
                        source.getCause() != DamageCause.FALL) { // No armor damage

                    for (int slot = 0; slot < 4; slot++) {
                        Item armor = damageArmor(this.inventory.getArmorItem(slot), damager, source.getDamage(), false, source.getCause());
                        inventory.setArmorItem(slot, armor, armor.getId() != BlockID.AIR);
                    }
                } else if (damager != null && source.getCause() != DamageCause.THORNS) { // Do post attack only
                    for (int slot = 0; slot < 4; slot++) {
                        Item armor = this.inventory.getArmorItem(slot);
                        for (Enchantment enchantment : armor.getEnchantments()) {
                            enchantment.doPostAttack(damager, this);
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        } else {
            return super.attack(source);
        }
    }

    protected Item damageArmor(Item armor, Entity damager, float damage, boolean shield, DamageCause cause) {
        if (armor.isUnbreakable() || armor instanceof ItemSkull || armor.getId() == (255 - BlockID.CARVED_PUMPKIN)) {
            return armor;
        }

        if (damager != null && cause != DamageCause.THORNS) {
            for (Enchantment enchantment : armor.getEnchantments()) {
                enchantment.doPostAttack(damager, this);
            }
        }

        Enchantment durability = armor.getEnchantment(Enchantment.ID_DURABILITY);
        if (durability != null
                && durability.getLevel() > 0
                && (100 / (durability.getLevel() + 1)) <= ThreadLocalRandom.current().nextInt(100)) {
            return armor;
        }

        if (shield) {
            armor.setDamage(armor.getDamage() + (damage >= 4.0f ? ((int) damage) : 1));
        } else {
            armor.setDamage(armor.getDamage() + Math.max((int) (damage / 4), 1));
        }

        if (armor.getDamage() >= armor.getMaxDurability()) {
            return Item.get(BlockID.AIR, 0, 0);
        }

        return armor;
    }


    protected double calculateEnchantmentProtectionFactor(Item item, EntityDamageEvent source) {
        double epf  = 0;

        for (Enchantment ench : item.getEnchantments()) {
            epf  += ench.getProtectionFactor(source);
        }

        return epf ;
    }

    @Override
    public void setOnFire(int seconds) {
        int level = 0;

        for (Item armor : this.inventory.getArmorContents()) {
            Enchantment fireProtection = armor.getEnchantment(Enchantment.ID_PROTECTION_FIRE);

            if (fireProtection != null && fireProtection.getLevel() > 0) {
                level = Math.max(level, fireProtection.getLevel());
            }
        }

        seconds = (int) (seconds * (1 - level * 0.15));

        super.setOnFire(seconds);
    }
}
