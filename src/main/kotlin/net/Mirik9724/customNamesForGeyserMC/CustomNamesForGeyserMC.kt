package net.Mirik9724.customNamesForGeyserMC

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.GameProfileRequestEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import com.velocitypowered.api.util.GameProfile
import net.elytrium.limboapi.api.Limbo
import net.elytrium.limboapi.api.LimboFactory
import net.elytrium.limboapi.api.chunk.Dimension
import net.elytrium.limboapi.api.chunk.VirtualWorld
import net.elytrium.limboapi.api.player.GameMode
import net.elytrium.limboapi.api.LimboSessionHandler
import net.kyori.adventure.text.Component
import org.bstats.charts.SingleLineChart
import org.bstats.velocity.Metrics
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.geyser.api.GeyserApi
import org.slf4j.Logger
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer


@Plugin(
    id = "customnamesforgeysermc",
    name = "CustomNamesForGeyserMC",
    version = "1.0",
    authors = ["Mirik9724"],
    dependencies = [
        Dependency(id = "geyser"),
        Dependency(id = "limboapi")
    ]
)
class CustomNamesForGeyserMC @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    private val metricsFactory: Metrics.Factory
) {
    lateinit var data: Map<String, Any>
    lateinit var factory: LimboFactory
    lateinit var nwFactory: Limbo
        private set
    data class LinkingUUID(
        val uuid: UUID,
        var fakeNick: String,
    )
    private val linkingUUID = mutableListOf<LinkingUUID>()

    private fun isBedrockPlayer(player: Player): Boolean {
        val api = GeyserApi.api()
        return api.isBedrockPlayer(player.uniqueId)
    }

    private fun isValidNick(checkeNick: String?): Boolean {
        if (checkeNick == null) return false
        if (checkeNick.length < 3 || checkeNick.length > 16) return false
        return checkeNick.matches("^[A-Za-z_][A-Za-z0-9_]*$".toRegex())
    }

    private fun genUUID(nick: String): UUID {
        return UUID.nameUUIDFromBytes("OfflinePlayer:$nick".toByteArray())
    }

    private fun Map<*, *>.getByPath(path: String): String {
        val keys = path.split(".")
        var current: Any? = this
        for (key in keys) {
            current = (current as? Map<*, *>)?.get(key) ?: return "null"
        }
        return current?.toString() ?: "null"
    }

    private fun initLimbo() {
        val nickWorld: VirtualWorld? = factory.createVirtualWorld(
            Dimension.THE_END,
            0.0, 64.0, 0.0,
            0f, 0f
        )

        val block = factory.createSimpleBlock("minecraft:barrier")
        nickWorld?.setBlock(0, 63, 0, block)

        nwFactory = factory.createLimbo(nickWorld).setName("nickWorld").setGameMode(GameMode.ADVENTURE)
    }


    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        logger.info("Starting")

        val folder = File("plugins/CustomNamesForGeyserMC")
        if (!folder.exists()) folder.mkdir()
        val file = File(folder, "config.yml")
        if (!file.exists()) {
            val inputStream = this::class.java.classLoader.getResourceAsStream("config.yml")
            if (inputStream != null) {
                file.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                logger.info("Config file copied from JAR")
            } else {
                logger.warn("Config file not found in JAR!")
            }
        }

        data = Yaml().load<Map<String, Any>>(file.inputStream())
            ?: emptyMap()
        logger.info("OK Config")

        factory = server.pluginManager
            .getPlugin("limboapi")
            .flatMap { it.getInstance() }
            .orElseThrow() as LimboFactory
        initLimbo()
        logger.info("OK Limbo")

        if(data.getByPath("useMetric") == "true") {
            val pluginId = 28818
            val metrics = metricsFactory.make(this, pluginId)

            metrics.addCustomChart(
                SingleLineChart("linked players") {
                    linkingUUID.size
                }
            )
            logger.info("ON Metrics")
        }

        if(data.getByPath("checkUpdates") == "true") {


        logger.info(data.getByPath("form.example"))
        logger.info("ON")

    }}

    @Subscribe
    fun onPlayerConnect(event: ServerConnectedEvent) {
        val player = event.player

        if (isBedrockPlayer(player)) {
            if (linkingUUID.find { it.uuid == player.uniqueId } != null) {
                return
            }
            server.scheduler.buildTask(this@CustomNamesForGeyserMC, Consumer<ScheduledTask> { task ->
                nwFactory.spawnPlayer(player, object : LimboSessionHandler{})
            }).delay(50, TimeUnit.MILLISECONDS).schedule()

            lateinit var wrongNick: CustomForm
            lateinit var nickForm: CustomForm.Builder

            wrongNick = CustomForm.builder()
                .title(data.getByPath("form.wrongTitle"))
                .label(data.getByPath("form.wrongNick"))
                .validResultHandler {
                    GeyserApi.api().sendForm(player.uniqueId, nickForm)
                }
                .closedResultHandler(Runnable{
                    GeyserApi.api().sendForm(player.uniqueId, wrongNick)
                })
                .build()


            nickForm = CustomForm.builder()
                .title(data.getByPath("form.title"))
                .input(data.getByPath("form.enterNick"), data.getByPath("form.example"))
                .validResultHandler { response ->
                    val newNick: String? = response.next() as? String
                    if (isValidNick(newNick) == false) {
                        GeyserApi.api().sendForm(player.uniqueId, wrongNick)
                    }
                    else {
                        linkingUUID.add(
                            LinkingUUID(player.uniqueId, newNick!!)
                        )
                        player.disconnect(Component.text(data.getByPath("reconnect")))
                    }
                }
                .closedResultHandler(Runnable{
                    GeyserApi.api().sendForm(player.uniqueId, nickForm)
                })
            GeyserApi.api().sendForm(player.uniqueId, nickForm)
        }
    }

    @Subscribe
    fun onGameProfileRequest(event: GameProfileRequestEvent) {
        val profile = event.gameProfile
        val origUUID = profile.id

        val foundNick = linkingUUID.find { it.uuid == origUUID }?.fakeNick
        if (foundNick != null) {
            val newProfile = GameProfile(
                genUUID(foundNick),
                foundNick,
                emptyList()
            )
//            GeyserApi.api().

//            GeyserApi.api().entry.setName(foundNick);

            event.setGameProfile(newProfile)
            linkingUUID.removeIf { it.uuid == origUUID }
//            logger.info("Removed linking uuid")
        }
    }
}
