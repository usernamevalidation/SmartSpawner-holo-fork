package github.nighter.smartspawner.hooks.economy.shops.providers.axsellwands;

import com.artillexstudios.axsellwands.libs.axintegrations.IntegrationManager;
import com.artillexstudios.axsellwands.libs.axintegrations.api.events.AxIntegrationsLoadEvent;
import com.artillexstudios.axsellwands.libs.axintegrations.api.events.AxIntegrationsReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Tells AxSellWands that SmartSpawner is available as a container integration.
 * Must register during AxIntegrationsLoadEvent — the IntegrationManager locks
 * after setup and rejects registrations at any other time.
 */
public class AxIntegrationsListener implements Listener {

    @EventHandler
    public void onLoad(AxIntegrationsLoadEvent event) {
        IntegrationManager.provideIntegration(SmartSpawnerContainerIntegration.class);
    }

    @EventHandler
    public void onReload(AxIntegrationsReloadEvent event) {
        IntegrationManager.provideIntegration(SmartSpawnerContainerIntegration.class);
    }
}