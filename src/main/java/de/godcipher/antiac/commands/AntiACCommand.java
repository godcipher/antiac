package de.godcipher.antiac.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import de.godcipher.antiac.AntiAC;
import de.godcipher.antiac.click.CPS;
import de.godcipher.antiac.click.ClickTracker;
import de.godcipher.antiac.config.Configuration;
import de.godcipher.antiac.detection.Check;
import de.godcipher.antiac.detection.violation.ViolationTracker;
import de.godcipher.antiac.messages.Colors;
import de.godcipher.antiac.messages.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@CommandAlias("antiac")
@Slf4j
public class AntiACCommand extends BaseCommand {

  private static final String ERROR_TITLE = "Error";
  private static final String SUCCESS_TITLE = "✔";

  private final Map<UUID, UUID> playerChecks = new HashMap<>();
  private final ClickTracker clickTracker;
  private final ViolationTracker violationTracker;
  private final boolean modernFeedback;

  public AntiACCommand(
      ClickTracker clickTracker, ViolationTracker violationTracker, Configuration configuration) {
    this.clickTracker = clickTracker;
    this.violationTracker = violationTracker;
    this.modernFeedback = configuration.getConfigOption("modern-feedback").asBoolean();
  }

  @Default
  @Description("Specify a subcommand")
  public void onDefault(Player player) {
    sendFeedback(
        player,
        Colors.PURPLE_MAUVE_COLOR,
        ERROR_TITLE,
        Messages.getString("command.specify_subcommand"));
  }

  @CatchUnknown
  @Description("Unknown subcommand")
  public void onUnknown(Player player) {
    sendFeedback(
        player,
        Colors.PURPLE_MAUVE_COLOR,
        ERROR_TITLE,
        Messages.getString("command.unknown_subcommand"));
  }

  @Subcommand("check")
  @CommandCompletion("@players")
  @CommandPermission("antiac.check")
  @Description("Check a player's CPS")
  public void onCheck(Player player, @Optional String targetName) {
    if (targetName == null) {
      sendFeedback(
          player,
          Colors.PURPLE_MAUVE_COLOR,
          ERROR_TITLE,
          Messages.getString("command.check.specify_player"));
      return;
    }

    Player target = player.getServer().getPlayer(targetName);
    if (target == null) {
      sendFeedback(
          player,
          Colors.PURPLE_MAUVE_COLOR,
          ERROR_TITLE,
          Messages.getString("command.check.player_not_found"));
      return;
    }

    playerChecks.put(player.getUniqueId(), target.getUniqueId());
    sendFeedback(
        player,
        Colors.PINE_GREEN_COLOR,
        SUCCESS_TITLE,
        Messages.getString("command.check.checking_player").replace("%player%", target.getName()));
    startCheckTask(player, target);
  }

  @Subcommand("cancel")
  @CommandPermission("antiac.check")
  @Description("Cancel the current check")
  public void onCancel(Player player) {
    if (!playerChecks.containsKey(player.getUniqueId())) {
      sendFeedback(
          player,
          Colors.PURPLE_MAUVE_COLOR,
          ERROR_TITLE,
          Messages.getString("command.cancel.not_checking"));
      return;
    }

    playerChecks.remove(player.getUniqueId());
    sendFeedback(
        player,
        Colors.PINE_GREEN_COLOR,
        SUCCESS_TITLE,
        Messages.getString("command.cancel.checking"));
  }

  @Subcommand("checks")
  @CommandPermission("antiac.checks")
  @Description("List all checks with activate/deactivate buttons")
  public void onChecks(Player player) {
    sendFeedback(
        player,
        Colors.PINE_GREEN_COLOR,
        SUCCESS_TITLE,
        Messages.getString("command.checks.listing"));

    // Space above the header
    player.spigot().sendMessage(new TextComponent(" "));

    // Header
    TextComponent header = createHeader("⬢ AntiAC Checks ⬢");
    player.spigot().sendMessage(header);

    List<Check> sortedChecksByName = getSortedChecks();
    for (Check check : sortedChecksByName) {
      TextComponent message = createCheckMessage(check);
      player.spigot().sendMessage(message);
    }

    // Footer
    TextComponent footer = createFooter("⬢ " + "—".repeat(18) + " ⬢");
    player.spigot().sendMessage(footer);
  }

  @Subcommand("activate")
  @CommandPermission("antiac.checks")
  @CommandCompletion("@checks")
  @Description("Activate a check")
  public void onActivate(Player player, String checkName) {
    Check check = AntiAC.getInstance().getCheckRegistry().getCheckByName(checkName);
    if (check != null) {
      if (check.isActivated()) {
        sendFeedback(
            player,
            Colors.PURPLE_MAUVE_COLOR,
            ERROR_TITLE,
            Messages.getString("command.check.already_active"));
        return;
      }

      check.setActivated(true);
      onChecks(player); // Resend the checks list
      sendFeedback(
          player,
          Colors.PINE_GREEN_COLOR,
          SUCCESS_TITLE,
          Messages.getString("command.check.activate").replace("%check%", check.getName()));
      log.debug("Check {} activated by {}", checkName, player.getName());
    } else {
      sendFeedback(
          player,
          Colors.PURPLE_MAUVE_COLOR,
          ERROR_TITLE,
          Messages.getString("command.check.not_found"));
    }
  }

  @Subcommand("deactivate")
  @CommandPermission("antiac.checks")
  @CommandCompletion("@checks")
  @Description("Deactivate a check")
  public void onDeactivate(Player player, String checkName) {
    Check check = AntiAC.getInstance().getCheckRegistry().getCheckByName(checkName);
    if (check != null) {
      if (!check.isActivated()) {
        sendFeedback(
            player,
            Colors.PURPLE_MAUVE_COLOR,
            ERROR_TITLE,
            Messages.getString("command.check.already_inactive"));
        return;
      }
      check.setActivated(false);
      onChecks(player); // Resend the checks list
      sendFeedback(
          player,
          Colors.PINE_GREEN_COLOR,
          SUCCESS_TITLE,
          Messages.getString("command.check.deactivate").replace("%check%", check.getName()));
      log.debug("Check {} deactivated by {}", checkName, player.getName());
    } else {
      sendFeedback(
          player,
          Colors.PURPLE_MAUVE_COLOR,
          ERROR_TITLE,
          Messages.getString("command.check.not_found"));
    }
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

          String message =
              String.format(
                  "%s%s | %s%s - %sViolations: %s%d",
                  Colors.BRASS_YELLOW_COLOR,
                  cps,
                  Colors.COPPER_ORANGE_COLOR,
                  maxCPS,
                  Colors.SLATE_GRAY_COLOR,
                  Colors.ROSEWOOD_RED_COLOR,
                  violationTracker.getViolationCount(target.getUniqueId()));

          player
              .spigot()
              .sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } else {
          cancel();
        }
      }
    }.runTaskTimer(AntiAC.getInstance(), 0, 2);
  }

  private CPS getPreviousCPS(UUID playerId) {
    List<CPS> cpsList = new ArrayList<>(clickTracker.getCPSList(playerId));
    return cpsList.stream().skip(Math.max(0, cpsList.size() - 2)).findFirst().orElse(CPS.EMPTY);
  }

  private void sendFeedback(Player player, ChatColor chatColor, String title, String subtitle) {
    if (!modernFeedback) {
      player.sendMessage(chatColor + title + " §7" + subtitle);
      return;
    }
    player.sendTitle(chatColor + title, "§7" + subtitle, 10, 70, 20);
  }

  private TextComponent createButton(String text, ChatColor color, String command, String tooltip) {
    TextComponent button = new TextComponent(text);
    button.setColor(color);
    button.setHoverEvent(
        new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(tooltip)));
    button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
    return button;
  }

  private String formatCheckName(String checkName) {
    return checkName.replaceAll("([a-z])([A-Z])", "$1 $2").replace("Check", "").trim();
  }

  private TextComponent createHeader(String text) {
    TextComponent header = new TextComponent(text);
    header.setColor(Colors.PURPLE_MAUVE_COLOR);
    header.setText(" ".repeat(10) + header.getText() + " ".repeat(10)); // Add padding for centering
    return header;
  }

  private TextComponent createFooter(String text) {
    TextComponent footer = new TextComponent(text);
    footer.setColor(Colors.PURPLE_MAUVE_COLOR);
    footer.setText(footer.getText());
    return footer;
  }

  /**
   * Get a sorted list of checks by name. Deactivated checks are always last.
   *
   * @return a sorted list of checks
   */
  private List<Check> getSortedChecks() {
    return AntiAC.getInstance().getCheckRegistry().getChecks().stream()
        .sorted(
            (check1, check2) -> {
              if (check1.isActivated() && !check2.isActivated()) {
                return -1;
              } else if (!check1.isActivated() && check2.isActivated()) {
                return 1;
              }
              return check1.getName().compareTo(check2.getName()); // Sort by name
            })
        .toList();
  }

  private TextComponent createCheckMessage(Check check) {
    TextComponent message = new TextComponent("  | ");
    message.setColor(Colors.SEPARATOR_COLOR);

    TextComponent checkName = new TextComponent(formatCheckName(check.getName()));
    checkName.setColor(check.isActivated() ? Colors.SLATE_GRAY_COLOR : Colors.SUNSET_ORANGE_COLOR);

    TextComponent activateButton =
        createButton(
            "[A]",
            Colors.PINE_GREEN_COLOR,
            "/antiac activate " + check.getName(),
            Colors.SLATE_GRAY_COLOR + "Activate this check");
    TextComponent deactivateButton =
        createButton(
            "[D]",
            Colors.ROSEWOOD_RED_COLOR,
            "/antiac deactivate " + check.getName(),
            Colors.SLATE_GRAY_COLOR + "Deactivate this check");

    message.addExtra(checkName);
    message.addExtra(" ");
    if (check.isActivated()) {
      message.addExtra(deactivateButton);
    } else {
      message.addExtra(activateButton);
    }

    return message;
  }
}
