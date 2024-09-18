package de.godcipher.antiac.commands;

import de.godcipher.antiac.AntiAC;
import de.godcipher.antiac.click.CPS;
import de.godcipher.antiac.click.ClickTracker;
import de.godcipher.antiac.detection.violation.ViolationTracker;
import de.godcipher.antiac.messages.Colors;
import de.godcipher.antiac.messages.Messages;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
public class AntiACCommand implements TabExecutor {

  private static final String ERROR_TITLE = "Error";
  private static final String SUCCESS_TITLE = "✔";

  private final List<String> subCommands = List.of("check", "cancel");
  private final Map<UUID, UUID> playerChecks = new HashMap<>();

  private final ClickTracker clickTracker;
  private final ViolationTracker violationTracker;

  @Override
  public boolean onCommand(
      @NotNull CommandSender commandSender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!(commandSender instanceof Player)) {
      commandSender.sendMessage(Messages.getString("command.only_player"));
      return true;
    }

    Player player = (Player) commandSender;

    if (args.length == 0) {
      sendTitle(
          player,
          Colors.ERROR_COLOR,
          ERROR_TITLE,
          Messages.getString("command.specify_subcommand"));
      return true;
    }

    String subCommand = args[0].toLowerCase();
    return switch (subCommand) {
      case "check" -> handleCheckCommand(player, args);
      case "cancel" -> handleCancelCommand(player);
      default -> {
        sendTitle(
            player,
            Colors.ERROR_COLOR,
            ERROR_TITLE,
            MessageFormat.format(
                Messages.getString("command.unknown_subcommand"), String.join(", ", subCommands)));
        yield true;
      }
    };
  }

  private boolean handleCheckCommand(Player player, String[] args) {
    if (args.length < 2) {
      sendTitle(
          player,
          Colors.ERROR_COLOR,
          ERROR_TITLE,
          Messages.getString("command.check.specify_player"));
      return true;
    }

    Player target = player.getServer().getPlayer(args[1]);
    if (target == null) {
      sendTitle(
          player,
          Colors.ERROR_COLOR,
          ERROR_TITLE,
          Messages.getString("command.check.player_not_found"));
      return true;
    }

    playerChecks.put(player.getUniqueId(), target.getUniqueId());
    sendTitle(player, Colors.SUCCESS_COLOR, SUCCESS_TITLE, "");
    startCheckTask(player, target);
    return true;
  }

  private void startCheckTask(Player player, Player target) {
    new BukkitRunnable() {
      int maxCPS;

      @Override
      public void run() {
        if (playerChecks.containsKey(player.getUniqueId())) {
          CPS cps = clickTracker.getLatestCPS(target.getUniqueId());
          CPS previousCPS = getPreviousCPS(target.getUniqueId());
          if (previousCPS.isEmpty()) {
            maxCPS = 0;
          }

          maxCPS = Math.max(maxCPS, cps.getCPS());

          String cpsPart = Colors.BRASS_YELLOW_COLOR + "" + cps;
          String separator = Colors.SEPARATOR_COLOR + " | ";
          String maxCpsPart = Colors.COPPER_ORANGE_COLOR + "" + maxCPS;
          String violationPart =
              Colors.SLATE_GRAY_COLOR
                  + "Violations: "
                  + Colors.ROSEWOOD_RED_COLOR
                  + violationTracker.getViolationCount(target.getUniqueId());

          StringBuilder message = new StringBuilder();
          message
              .append(cpsPart)
              .append(separator)
              .append(maxCpsPart)
              .append(Colors.SEPARATOR_COLOR)
              .append(" - ")
              .append(violationPart);

          player
              .spigot()
              .sendMessage(
                  ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message.toString()));
        } else {
          cancel();
        }
      }
    }.runTaskTimer(AntiAC.getInstance(), 0, 2);
  }

  private CPS getPreviousCPS(UUID playerId) {
    List<CPS> cpsList = clickTracker.getCPSList(playerId);
    return cpsList.size() > 1 ? cpsList.get(cpsList.size() - 2) : CPS.EMPTY;
  }

  private boolean handleCancelCommand(Player player) {
    if (!playerChecks.containsKey(player.getUniqueId())) {
      sendTitle(
          player,
          Colors.ERROR_COLOR,
          ERROR_TITLE,
          Messages.getString("command.cancel.not_checking"));
      return true;
    }

    playerChecks.remove(player.getUniqueId());
    sendTitle(player, Colors.SUCCESS_COLOR, SUCCESS_TITLE, "");
    return true;
  }

  @Override
  public @Nullable List<String> onTabComplete(
      @NotNull CommandSender commandSender,
      @NotNull Command command,
      @NotNull String alias,
      @NotNull String[] args) {
    if (args.length == 1) {
      return subCommands;
    } else {
      return Bukkit.getOnlinePlayers().stream()
          .map(Player::getName)
          .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
          .toList();
    }
  }

  private void sendTitle(Player player, ChatColor chatColor, String title, String subtitle) {
    player.sendTitle(chatColor + title, "§7" + subtitle, 10, 70, 20);
  }
}
