package dev.enderman.minecraft.plugins.projectiles.better.listener;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.jetbrains.annotations.NotNull;

import dev.enderman.minecraft.plugins.projectiles.better.BetterProjectilesPlugin;

public class SnowGolemSpawnListener implements Listener {

    private final BetterProjectilesPlugin plugin;

    public SnowGolemSpawnListener(BetterProjectilesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSnowGolemSpawn(@NotNull EntitySpawnEvent event) {
        if (event.getEntity() instanceof Snowman snowGolem) {
            AttributeInstance maxHealthAttribute = snowGolem.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            assert maxHealthAttribute != null;

            YamlConfiguration configuration = (YamlConfiguration) plugin.getConfig();
            double snowGolemHealth = configuration.getDouble("snow-golems.health");

            maxHealthAttribute.addModifier(new AttributeModifier(plugin.getSnowGolemHealthIncreaseAttributeModifierKey(), snowGolemHealth - snowGolem.getHealth(), Operation.ADD_NUMBER));

            snowGolem.setHealth(snowGolemHealth);
        }
    }
}