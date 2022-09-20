package com.github.MitzDK;

import com.google.inject.Provides;

import java.util.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import static net.runelite.api.Varbits.DIARY_KARAMJA_EASY;
import static net.runelite.api.Varbits.DIARY_KARAMJA_HARD;
import static net.runelite.api.Varbits.DIARY_KARAMJA_MEDIUM;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Drop Sounds",
	description = "Plays sounds when loot drops",
	tags = {"announce", "sound"}
)

public class DropSoundsCompletedPlugin extends Plugin
{
	@Inject
	private Client client;

	@Getter(AccessLevel.PACKAGE)
	@Inject
	private ClientThread clientThread;

	@Inject
	private SoundEngine soundEngine;
	@Inject
	private DropSoundsConfig config;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private OkHttpClient okHttpClient;

	// Killcount and new pb patterns from runelite/ChatCommandsPlugin
	//private static final AccountSession session = sessionManager.getAccountSession();
	//private static final String CurrentUser = session.getUsername();

	private final Map<Skill, Integer> oldExperience = new EnumMap<>(Skill.class);
	private final Map<Integer, Integer> oldAchievementDiaries = new HashMap<>();

	private int lastLoginTick = -1;
	private int lastGEOfferTick = -1;
	private int lastZulrahKillTick = -1;

	@Override
	protected void startUp() throws Exception
	{
		executor.submit(() -> {
			SoundFileManager.ensureDownloadDirectoryExists();
			SoundFileManager.downloadAllMissingSounds(okHttpClient);
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		oldExperience.clear();
		oldAchievementDiaries.clear();
	}


	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch(event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
			case LOGGING_IN:
			case LOGIN_SCREEN_AUTHENTICATOR:
				oldExperience.clear();
				oldAchievementDiaries.clear();
			case CONNECTION_LOST:
				// set to -1 here in-case of race condition with varbits changing before this handler is called
				// when game state becomes LOGGED_IN
				lastLoginTick = -1;
				break;
			case LOGGED_IN:
				lastLoginTick = client.getTickCount();
				break;
		}
	}

	List<String> getHighlights()
	{
		final String configNpcs = config.itemsToNotify();

		if (configNpcs.isEmpty())
		{
			return Collections.emptyList();
		}

		return Text.fromCSV(configNpcs);
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) {
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.CLAN_GIM_MESSAGE && chatMessage.getType() != ChatMessageType.SPAM) {
			return;
		}
		if(config.chatSelect() == ChatSelect.CLAN_MESSAGE){
			if (chatMessage.getType() == ChatMessageType.CLAN_MESSAGE && chatMessage.getMessage().contains(client.getLocalPlayer().getName() + " received a drop")) {
				soundEngine.playClip(Sound.VALUABLE_DROP);
			}
		}
		if(config.chatSelect() == ChatSelect.CLAN_GIM_MESSAGE){
			if (chatMessage.getType() == ChatMessageType.CLAN_GIM_MESSAGE && chatMessage.getMessage().contains(client.getLocalPlayer().getName() + " received a drop")) {
				soundEngine.playClip(Sound.VALUABLE_DROP);
			}
		}
		if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE && chatMessage.getMessage().contains("Untradeable drop: ")) {
			if(getHighlights().contains(chatMessage.getMessage().split(": ")[1])){
				soundEngine.playClip(Sound.UNTRADEABLE_DROP);
			}
		}
	}


	@Provides
	DropSoundsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropSoundsConfig.class);
	}

// Disabled - fires continuously while spinner arrow is held - when this is avoidable, can enable
//	@Subscribe
//	public void onConfigChanged(ConfigChanged event) {
//		if (CEngineerCompletedConfig.GROUP.equals(event.getGroup())) {
//			if ("announcementVolume".equals(event.getKey())) {
//				soundEngine.playClip(Sound.LEVEL_UP);
//			}
//		}
//	}
}