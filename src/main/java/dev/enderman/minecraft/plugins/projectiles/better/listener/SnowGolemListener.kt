package dev.enderman.minecraft.plugins.projectiles.better.listener

import dev.enderman.minecraft.plugins.projectiles.better.BetterProjectilesPlugin
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.entity.Snowman
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SnowGolemListener(private val plugin: BetterProjectilesPlugin) : Listener {

  private val snowGolemHealthMap = mutableMapOf<UUID, Double>()

  @EventHandler
  fun onSnowGolemSpawn(event: EntitySpawnEvent) {
    val entity = event.entity

    if (entity is Snowman) {
      val maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH)!!
      val configuration = plugin.config as YamlConfiguration
      val snowGolemHealth = configuration.getDouble("snow-golems.health")

      maxHealthAttribute.addModifier(
        AttributeModifier(plugin.snowGolemHealthIncreaseAttributeModifierKey, snowGolemHealth - entity.health, AttributeModifier.Operation.ADD_NUMBER)
      )

      entity.health = snowGolemHealth

      val detectionRangeAttribute = entity.getAttribute(Attribute.FOLLOW_RANGE)!!
      val extraDetectionRange = configuration.getDouble("snow-golems.extra-detection-range")

      detectionRangeAttribute.addModifier(
        AttributeModifier(plugin.snowGolemDetectionRangeIncreaseAttributeModifierKey, extraDetectionRange, AttributeModifier.Operation.ADD_NUMBER)
      )

      var task: BukkitTask? = null
      task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
        plugin.logger.info("Running runnable...")

        if (entity.isDead) {
          plugin.logger.info("Snowman is DEAD! Cancelling runnable...")
          task!!.cancel()
          return@Runnable
        }

        val world = entity.world
        val location = entity.location

        val temperature = world.getTemperature(location.blockX, location.blockY, location.blockZ)

        plugin.logger.info("TEMPERATURE RECORDER: $temperature")

        if (temperature > 0.0) return@Runnable

        val isRegenerating = entity.hasPotionEffect(PotionEffectType.REGENERATION)

        plugin.logger.info("IS ALREADY REGENERATING: $isRegenerating")

        if (isRegenerating) return@Runnable

        val isSnowing = !world.isClearWeather

        plugin.logger.info("WEATHER STATUS: $isSnowing")

        entity.addPotionEffect(
          PotionEffect(PotionEffectType.REGENERATION, (if (isSnowing) 7 else 2) * 20, if (isSnowing) 2 else 0, true, false)
        )
      }, 100L, 100L)
    }
  }

  @EventHandler
  fun onSnowGolemSnowballShoot(event: ProjectileLaunchEvent) {
    val projectile = event.entity

    if (projectile.shooter is Snowman) {
      val dataContainer = projectile.persistentDataContainer

      dataContainer.set(plugin.snowGolemSnowballKey, PersistentDataType.BOOLEAN, true)
    }
  }

  @EventHandler
  private fun onSnowGolemProjectileHit(event: ProjectileHitEvent) {
    val projectile = event.entity

    if (projectile !is Snowball) return

    val shooter = projectile.shooter

    if (shooter !is Snowman) return

    val dataContainer = projectile.persistentDataContainer
    val isSnowGolemProjectile = dataContainer.get(plugin.snowGolemSnowballKey, PersistentDataType.BOOLEAN) == true

    if (!isSnowGolemProjectile) return

    val hitEntity = event.hitEntity

    if (hitEntity is Snowman) return
    if (hitEntity !is LivingEntity) return

    val configurationSection = plugin.config.getConfigurationSection("projectiles.snowball.snowman-snowball")!!

    val damage = configurationSection.getDouble("damage")

    val source = DamageSource
      .builder(DamageType.ARROW)
      .withDamageLocation(hitEntity.location)
      .withDirectEntity(shooter)
      .withCausingEntity(shooter)
      .build()

    hitEntity.damage(damage, source)

    val slownessSettings = configurationSection.getConfigurationSection("slowness")!!
    val blindnessSettings = configurationSection.getConfigurationSection("blindness")!!

    val slownessEnabled = slownessSettings.getBoolean("enabled")
    val blindnessEnabled = blindnessSettings.getBoolean("enabled")

    if (slownessEnabled) {
      val duration = blindnessSettings.getInt("duration-seconds")
      val potency = blindnessSettings.getInt("potency")

      hitEntity.addPotionEffect(
        PotionEffect(PotionEffectType.SLOWNESS, duration * 20, potency - 1, true, true, true)
      )
    }

    if (blindnessEnabled) {
      val duration = blindnessSettings.getInt("duration-seconds")
      val potency = blindnessSettings.getInt("potency")

      hitEntity.addPotionEffect(
        PotionEffect(PotionEffectType.BLINDNESS, duration * 20, potency - 1, true, true, true)
      )
    }
  }

  @EventHandler
  fun onSnowGolemRightClick(event: PlayerInteractAtEntityEvent) {
    val entity = event.rightClicked

    if (entity !is Snowman) return
    if (event.hand != EquipmentSlot.HAND) return

    val health = entity.health

    if (health <= 0) return

    val maxHealthAttributeInstance = entity.getAttribute(Attribute.MAX_HEALTH)!!

    val maxHealth = maxHealthAttributeInstance.value

    val player = event.player
    val inventory = player.inventory

    val healthIncrease: Double

    // Snow golem has maxHealth health.
    // Snow golem is made of 2 snow blocks.
    // Snow golem is made of 8 snowballs.
    var heldItem = inventory.itemInMainHand

    when (heldItem.type) {
      Material.SNOW_BLOCK -> healthIncrease = maxHealth / 2.0
      Material.SNOWBALL -> healthIncrease = maxHealth / 8.0
      else -> {
        heldItem = inventory.itemInOffHand

        healthIncrease = when (heldItem.type) {
          Material.SNOW_BLOCK -> maxHealth / 2.0
          Material.SNOWBALL -> maxHealth / 8.0
          else -> {
            onSnowGolemSnowTake(player, entity)
            return
          }
        }
      }
    }

    val isLargeHealthIncrease = healthIncrease == maxHealth / 2.0

    val finalHealth = min(health + healthIncrease, maxHealth)
    val actualAmountHealed = finalHealth - health

    if (actualAmountHealed != 0.0) {
      event.isCancelled = true

      entity.health = finalHealth
      entity.world.spawnParticle(Particle.HEART, entity.location, actualAmountHealed.toInt(), 0.5, 0.25, 0.5)

      val mapValue = snowGolemHealthMap[entity.uniqueId]
      if (mapValue != null) snowGolemHealthMap[entity.uniqueId] = finalHealth

      if (player.gameMode != GameMode.CREATIVE) {
        heldItem.amount -= 1

        if (isLargeHealthIncrease && actualAmountHealed != maxHealth / 2.0) {
          val snowBallCompensation = (maxHealth / 2.0 - actualAmountHealed).toInt()
          player.inventory.addItem(ItemStack(Material.SNOWBALL, snowBallCompensation))
        }
      }
    }
  }

  private fun onSnowGolemSnowTake(player: Player, golem: Snowman) {
    if (golem.noDamageTicks > 10) return
    if (player.inventory.itemInMainHand.type != Material.AIR || player.inventory.itemInOffHand.type != Material.AIR) return

    val health = golem.health

    val maxHealthAttributeInstance = golem.getAttribute(Attribute.MAX_HEALTH)!!
    val maxHealth = maxHealthAttributeInstance.value

    val isLargeHealthDecrease = player.isSneaking && health >= maxHealth / 2.0

    val healthDecrease = if (isLargeHealthDecrease) maxHealth / 2.0 else maxHealth / 8.0

    golem.damage(healthDecrease, player)
  }

  @EventHandler
  private fun onSnowGolemDeath(event: EntityDeathEvent) {
    val entity = event.entity
    if (entity !is Snowman) return
    event.drops.clear()
    if (entity.isDerp) return
    event.drops.add(ItemStack(Material.CARVED_PUMPKIN))
  }

  @EventHandler
  private fun onSnowGolemDamage(event: EntityDamageEvent) {
    val entity = event.entity
    if (entity !is Snowman) return
    if (entity.noDamageTicks > 10) return

    val damageType = event.damageSource.damageType

    plugin.logger.info("Damage type: ${damageType}")

    val world = entity.world
    val location = entity.location

    if (damageType == DamageType.ON_FIRE) {
      val particleLocation = location.clone().add(0.0, 1.0, 0.0)

      world.spawnParticle(Particle.FALLING_WATER, particleLocation, 3, 0.3, 0.25, 0.3)
      return
    }

    if (damageType == DamageType.DROWN) return

    val maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH)!!
    val maxHealth = maxHealthAttribute.value

    plugin.logger.info("Max Health: $maxHealth")
    plugin.logger.info("Map value: ${snowGolemHealthMap[entity.uniqueId]}")

    val previousHealth = snowGolemHealthMap[entity.uniqueId] ?: maxHealth
    val health = max(previousHealth - event.finalDamage, 0.0)

    plugin.logger.info("Health: $health")
    plugin.logger.info("Previous health: $previousHealth")

    if (health == 0.0) snowGolemHealthMap.remove(entity.uniqueId) else snowGolemHealthMap[entity.uniqueId] = health

    val currentSnowballs = health.toInt()
    val previousSnowballs = previousHealth.toInt()

    if (currentSnowballs != previousSnowballs) {
      val lostSnowballCount = min((previousSnowballs - currentSnowballs).toDouble(), previousHealth).toInt()

      val snowballsToDrop = lostSnowballCount % 4
      val snowBlocksToDrop = (lostSnowballCount - snowballsToDrop) / 4

      if (snowBlocksToDrop != 0) {
        world.dropItemNaturally(location, ItemStack(Material.SNOW_BLOCK, snowBlocksToDrop))
      }

      if (snowballsToDrop != 0) {
        world.dropItemNaturally(location, ItemStack(Material.SNOWBALL, snowballsToDrop))
      }
    }
  }

  @EventHandler
  private fun onSnowGolemProvoked(event: EntityDamageByEntityEvent) {
    val entity = event.entity
    val attacker = event.damager

    if (entity !is Snowman) return
    if (attacker is Snowman) return
    if (attacker !is LivingEntity) return

    if (attacker is Player && attacker.gameMode == GameMode.CREATIVE) return

    entity.target = attacker
  }

  @EventHandler
  private fun onSnowGolemSnowballHit(event: ProjectileHitEvent) {
    val hitEntity = event.hitEntity
    val projectile = event.entity

    if (hitEntity !is Snowman) return
    if (projectile !is Snowball) return

    event.isCancelled = true

    val hitLocation = projectile.location

    projectile.remove()
    hitEntity.world.spawnParticle(Particle.SNOWFLAKE, hitLocation, 2, 0.1, 0.1, 0.1)

    val faceLocation = hitEntity.eyeLocation

    val distanceSquared = hitLocation.distanceSquared(faceLocation)
    if (distanceSquared <= 0.80) return

    plugin.logger.info("Distance squared: $distanceSquared")
    plugin.logger.info("Distance: ${sqrt(distanceSquared)}")

    val health = hitEntity.health
    val maxHealthAttribute = hitEntity.getAttribute(Attribute.MAX_HEALTH)!!
    val maxHealth = plugin.config.getDouble("snow-golems.health")

    val finalHealth = health + maxHealth / 8.0

    var modifier = maxHealthAttribute.getModifier(plugin.snowGolemDynamicHealthIncreaseKey)

    if (modifier == null) {
      modifier = AttributeModifier(plugin.snowGolemDynamicHealthIncreaseKey, finalHealth - maxHealth, AttributeModifier.Operation.ADD_NUMBER)
    } else {
      maxHealthAttribute.removeModifier(plugin.snowGolemDynamicHealthIncreaseKey)
    }

    maxHealthAttribute.addModifier(modifier)

    hitEntity.health = finalHealth

    val mapValue = snowGolemHealthMap[hitEntity.uniqueId]
    if (mapValue != null) snowGolemHealthMap[hitEntity.uniqueId] = finalHealth
  }
}
