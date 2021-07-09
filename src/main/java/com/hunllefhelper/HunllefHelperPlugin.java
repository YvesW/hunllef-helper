package com.hunllefhelper;

import com.google.inject.Provides;
import static com.hunllefhelper.PluginConstants.ATTACK_DURATION;
import static com.hunllefhelper.PluginConstants.COUNTER_INTERVAL;
import static com.hunllefhelper.PluginConstants.INITIAL_COUNTER;
import static com.hunllefhelper.PluginConstants.REGION_IDS_GAUNTLET;
import static com.hunllefhelper.PluginConstants.ROTATION_DURATION;
import static com.hunllefhelper.PluginConstants.SOUND_MAGE;
import static com.hunllefhelper.PluginConstants.SOUND_ONE;
import static com.hunllefhelper.PluginConstants.SOUND_RANGE;
import static com.hunllefhelper.PluginConstants.SOUND_TWO;
import com.hunllefhelper.ui.HunllefHelperPluginPanel;
import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Hunllef Helper"
)
public class HunllefHelperPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private HunllefHelperConfig config;

	private HunllefHelperPluginPanel panel;

	private ScheduledExecutorService executorService;
	private int counter;
	private boolean isRanged;
	private boolean wasInInstance;

	private NavigationButton navigationButton;

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(HunllefHelperPluginPanel.class);

		navigationButton = NavigationButton
			.builder()
			.tooltip("Hunllef Helper")
			.icon(ImageUtil.loadImageResource(getClass(), "/nav-icon.png"))
			.priority(100)
			.panel(panel)
			.build();

		panel.setCounterActiveState(false);

		if (config.autoHide())
		{
			wasInInstance = isInTheGauntlet();
			updateNavigationBar(wasInInstance, true);
		}
		else
		{
			updateNavigationBar(true, false);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		updateNavigationBar(false, false);
		if (executorService != null)
		{
			executorService.shutdownNow();
			executorService = null;
		}
		panel = null;
		navigationButton = null;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!config.autoHide())
		{
			return;
		}

		boolean isInInstance = isInTheGauntlet();
		if (isInInstance != wasInInstance)
		{
			updateNavigationBar(isInInstance, true);
			wasInInstance = isInInstance;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		updateNavigationBar((!config.autoHide() || isInTheGauntlet()), false);
	}

	public void start()
	{
		isRanged = true;
		counter = INITIAL_COUNTER;
		panel.setStyle("Ranged", Color.GREEN);
		panel.setCounterActiveState(true);

		executorService = Executors.newSingleThreadScheduledExecutor();
		executorService.scheduleAtFixedRate(this::tickCounter, 0, COUNTER_INTERVAL, TimeUnit.MILLISECONDS);
	}

	public void trample()
	{
		counter += ATTACK_DURATION;
	}

	public void reset()
	{
		executorService.shutdown();
		panel.setCounterActiveState(false);
	}

	private void tickCounter()
	{
		counter -= COUNTER_INTERVAL;
		panel.setTime(counter + 1000);

		if (counter == 2000)
		{
			playSoundClip(SOUND_TWO);
			return;
		}

		if (counter == 1000)
		{
			playSoundClip(SOUND_ONE);
			return;
		}

		if (counter <= 0)
		{
			if (isRanged)
			{
				playSoundClip(SOUND_MAGE);
				panel.setStyle("Mage", Color.CYAN);
			}
			else
			{
				playSoundClip(SOUND_RANGE);
				panel.setStyle("Ranged", Color.GREEN);
			}

			isRanged = !isRanged;
			counter = ROTATION_DURATION;
		}
	}

	@Provides
	HunllefHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HunllefHelperConfig.class);
	}

	private void playSoundClip(String soundFile)
	{
		if (config.mute())
		{
			return;
		}

		try
		{
			InputStream audioSource = getClass().getResourceAsStream(soundFile);
			BufferedInputStream bufferedStream = new BufferedInputStream(audioSource);
			AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedStream);

			Clip clip = AudioSystem.getClip();
			clip.open(audioInputStream);
			clip.start();
		}
		catch (Exception ex)
		{
			log.error(ex.getMessage());
		}
	}

	private boolean isInTheGauntlet()
	{
		Player player = client.getLocalPlayer();

		if (player == null)
		{
			return false;
		}

		int regionId = WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID();
		return REGION_IDS_GAUNTLET.contains(regionId);
	}

	private void updateNavigationBar(boolean enable, boolean selectPanel)
	{
		if (enable)
		{
			clientToolbar.addNavigation(navigationButton);
			if (selectPanel)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (!navigationButton.isSelected())
					{
						navigationButton.getOnSelect().run();
					}
				});
			}
		}
		else
		{
			navigationButton.setSelected(false);
			clientToolbar.removeNavigation(navigationButton);
		}
	}
}
