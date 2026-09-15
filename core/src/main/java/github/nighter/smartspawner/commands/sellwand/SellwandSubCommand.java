package github.nighter.smartspawner.commands.sellwand;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.language.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

public class SellwandSubCommand extends BaseSubCommand {

    private final MessageService messageService;
    private final SellwandGUI gui;

    public SellwandSubCommand(SmartSpawner plugin, SellwandGUI gui) {
        super(plugin);
        this.messageService = plugin.getMessageService();
        this.gui = gui;
    }

    @Override
    public String getName() {
        return "sellwand";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.sellwand";
    }

    @Override
    public String getDescription() {
        return "Sacrifice a sellwand to apply its multiplier to your spawner sells";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        if (!isPlayer(context.getSource().getSender())) {
            return 0;
        }
        Player player = getPlayer(context.getSource().getSender());
        if (player == null) return 0;

        if (!plugin.hasSellIntegration()) {
            messageService.sendMessage(player, "prices.not_available");
            return 0;
        }

        gui.open(player);
        return 1;
    }
}