package de.neocraftr.griefergames.server;

import java.util.*;

import de.neocraftr.griefergames.chat.*;
import de.neocraftr.griefergames.listener.*;
import de.neocraftr.griefergames.modules.*;
import de.neocraftr.griefergames.settings.ModSettings;
import de.neocraftr.griefergames.GrieferGames;
import de.neocraftr.griefergames.utils.Helper;
import net.labymod.api.LabyModAPI;
import net.labymod.api.events.*;
import net.labymod.ingamegui.ModuleCategoryRegistry;
import net.labymod.main.lang.LanguageManager;
import net.labymod.servermanager.ChatDisplayAction;
import net.labymod.servermanager.Server;
import net.labymod.settings.elements.SettingsElement;
import net.labymod.utils.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class GrieferGamesServer extends Server {

	private boolean modulesLoaded = false;

	public GrieferGamesServer() {
		super("GrieferGames", GrieferGames.SERVER_IP, GrieferGames.SECOND_SERVER_IP);

		ModuleCategoryRegistry.loadCategory(getGG().getModuleCategory());

		getGG().setBoosterModule(new BoosterModule());
		new FlyModule();
		new NicknameModule();
		new DelayModule();
		new IncomeModule();
		new RedstoneModule();
		new ClearLagModule();

		// add Chat Modules
		getGG().addChatModule(new PreventCommandFailure());
		getGG().addChatModule(new ClearChat());
		getGG().addChatModule(new Blanks());
		getGG().addChatModule(new SupremeBlanks());
		getGG().addChatModule(new News());
		getGG().addChatModule(new PrivateMessage());
		getGG().addChatModule(new Payment());
		getGG().addChatModule(new Bank());
		getGG().addChatModule(new ItemRemover());
		getGG().addChatModule(new MobRemover());
		getGG().addChatModule(new PlotChat());
		getGG().addChatModule(new Vote());
		getGG().addChatModule(new GlobalMessage());
		getGG().addChatModule(new LobbyHub());
		getGG().addChatModule(new Realname());
		getGG().addChatModule(new IgnoreList());
		getGG().addChatModule(new AntiMagicClanTag());
		getGG().addChatModule(new Teleport());
		getGG().addChatModule(new Nickname());
		getGG().addChatModule(new Mention());
		getGG().addChatModule(new AntiMagicPrefix());
		getGG().addChatModule(new ChatTime());

		getApi().registerForgeListener(new KeyInputListener());
		getApi().registerForgeListener(new TickListener());
		getApi().getEventManager().register(new PluginMessageListener());
		getApi().getEventManager().register(new MessageReceiveListener());
		getApi().getEventManager().registerOnIncomingPacket(new ServerPacketListener());

		getApi().getEventManager().register(new MessageModifyChatEvent() {
			@Override
			@SubscribeEvent(priority = EventPriority.HIGH)
			public Object onModifyChatMessage(Object o) {
				return modifyChatMessage(o);
			}

		});

		getApi().getEventManager().register(new MessageSendEvent() {
			public boolean onSend(String message) {
				if (!getSettings().isModEnabled() || !getGG().isOnGrieferGames()) return false;

				getGG().setLastActiveTime(System.currentTimeMillis());

				if(message.toLowerCase().startsWith("/ggdebug")) {
					getGG().getApi().displayMessageInChat("§7----------- "+GrieferGames.PREFIX+"§7----------- ");
					getGG().getApi().displayMessageInChat("§7Rank: §e" +getGG().getPlayerRank());
					getGG().getApi().displayMessageInChat("§7Server: §e" +getGG().getSubServer());
					return true;
				}

				if(message.toLowerCase().startsWith("/ggmessage")) {
					getGG().getApi().displayMessageInChat(getHelper().colorize(message.replace("/ggmessage", "").trim()));
					return true;
				}

				List<Chat> chatModules = getGG().getChatModules();
				for (Chat chatModule : chatModules) {
					if (chatModule.doActionCommandMessage(message)) {
						return chatModule.commandMessage(message);
					}
				}

				return false;
			}
		});

		getApi().getEventManager().registerOnQuit(new Consumer<net.labymod.utils.ServerData>() {
			@Override
			public void accept(net.labymod.utils.ServerData serverData) {
				getGG().setOnGrieferGames(false);
				getGG().setSubServer("");
			}
		});

		getApi().getEventManager().register(new RenderIngameOverlayEvent() {
			@Override
			public void onRender(float v) {
				getGG().getPlotSwitchGui().render();
			}
		});
	}

	@Override
	public ChatDisplayAction handleChatMessage(String unformatted, String formatted) throws Exception {
		if (!getSettings().isModEnabled() || !getGG().isOnGrieferGames()) return ChatDisplayAction.NORMAL;

		try {
			if (getSettings().isFilterDuplicateMessages() && getGG().getFilterDuplicateLastMessage().equals(formatted)) {
				return ChatDisplayAction.HIDE;
			}
			getGG().setFilterDuplicateLastMessage(formatted);

			List<Chat> chatModules = getGG().getChatModules();
			for (Chat chatModule : chatModules) {
				if (chatModule.doActionHandleChatMessage(unformatted, formatted)) {
					ChatDisplayAction retVal = chatModule.handleChatMessage(unformatted, formatted);
					if (retVal != ChatDisplayAction.NORMAL) {
						return retVal;
					}
				}
			}

			int status = getHelper().isVanishMessage(unformatted);
			if (status >= 0) {
				getGG().setVanishActive(status > 0);
				return ChatDisplayAction.NORMAL;
			}

			status = getHelper().isGodmodeMessage(unformatted);
			if (status >= 0) {
				getGG().setGodActive(status > 0);
				return ChatDisplayAction.NORMAL;
			}

			status = getHelper().isAuraMessage(unformatted);
			if (status >= 0) {
				getGG().setAuraActive(status > 0);
				return ChatDisplayAction.NORMAL;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ChatDisplayAction.NORMAL;
	}

	public Object modifyChatMessage(Object o) {
		if (!getSettings().isModEnabled() || !getGG().isOnGrieferGames()) return o;

		try {
			IChatComponent msg = (IChatComponent) o;

			// Brings locally sent messages in the proper format
			if(!msg.getUnformattedTextForChat().equals("")) {
				IChatComponent newMsg = new ChatComponentText("");
				newMsg.getSiblings().add(msg);
				msg = newMsg;
			}

			List<Chat> chatModules = getGG().getChatModules();
			for (Chat chatModule : chatModules) {
				if (chatModule.doActionModifyChatMessage(msg)) {
					msg = chatModule.modifyChatMessage(msg);
				}
			}

			return msg;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return o;
	}

	@Override
	public void onJoin(ServerData serverData) {
		getGG().setOnGrieferGames(true);
		getGG().setLastLabyChatSubServer("");
		getGG().setLastDiscordSubServer("");

		Thread thread = new Thread() {
			public void run() {
				try {
					Thread.sleep(500);
					int errorCount = 0;
					while(errorCount < 3) {
						if(getGG().getHelper().loadPlayerRank()) {
							if (!modulesLoaded) {
								modulesLoaded = true;

								if (getGG().getPlayerRankGroups().hasGodMode(getGG().getPlayerRank())) {
									new GodmodeModule();
								}
								if (getGG().getPlayerRankGroups().hasAura(getGG().getPlayerRank())) {
									new AuraModule();
								}
								if (getGG().getPlayerRankGroups().hasVanish(getGG().getPlayerRank())) {
									new VanishModule();
								}
							}
							if(getGG().getSettings().isAutoPortl()) {
								Minecraft.getMinecraft().thePlayer.sendChatMessage("/portal");
							}
							break;
						} else {
							errorCount++;
							Thread.sleep(500);
						}
					}
					if(errorCount >= 3) {
						getGG().getApi().displayMessageInChat(GrieferGames.PREFIX+"§4"+ LanguageManager.translateOrReturnKey("message_gg_error")+
								": §c"+LanguageManager.translateOrReturnKey("message_gg_rankError"));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		thread.start();
	}

	@Override
	public void fillSubSettings(List<SettingsElement> settings) {}

	@Override
	public void handlePluginMessage(String channel, PacketBuffer packetBufferOrig) {}

	@Override
	public void handleTabInfoMessage(TabListEvent.Type tabInfoType, String formatted, String clean) throws Exception {}

	private GrieferGames getGG() {
		return GrieferGames.getGriefergames();
	}

	private ModSettings getSettings() {
		return getGG().getSettings();
	}

	private Helper getHelper() {
		return GrieferGames.getGriefergames().getHelper();
	}

	private LabyModAPI getApi() {
		return GrieferGames.getGriefergames().getApi();
	}
}