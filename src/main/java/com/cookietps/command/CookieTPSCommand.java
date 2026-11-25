package com.cookietps.command;

import com.cookietps.CookieTPSPlugin;
import com.cookietps.api.AdminEditorController;
import com.cookietps.api.InputHandler;
import com.cookietps.config.ConfigManager;
import com.cookietps.config.PlayerPreferencesStore;
import com.cookietps.config.ProfileManager;
import com.cookietps.editor.EditorParameters;
import com.cookietps.editor.VisualEditorManager;
import com.cookietps.model.PlayerPreferences;
import com.cookietps.model.RenderTransform;
import com.cookietps.model.ScreenProfile;
import com.cookietps.model.ScreenType;
import com.cookietps.model.SpatialSession;
import com.cookietps.model.ThirdPersonSettings;
import com.cookietps.model.ViewMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CookieTPSCommand implements CommandExecutor, TabCompleter {
    public static final String USE_PERMISSION = "cookietps.use";
    public static final String ADMIN_PERMISSION = VisualEditorManager.ADMIN_PERMISSION;

    private final CookieTPSPlugin plugin;

    public CookieTPSCommand(CookieTPSPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleStatus(sender);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status", "help" -> handleStatus(sender);
            case "toggle" -> handleToggle(sender);
            case "mode" -> handleMode(sender, args);
            case "open" -> handleOpen(sender, args);
            case "close" -> handleClose(sender);
            case "editor" -> handleEditor(sender, args);
            case "profile" -> handleProfile(sender, args);
            case "filter" -> handleFilter(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage(prefixed("Unknown subcommand '" + args[0] + "'. Use /cookietps status.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean canUse = sender.hasPermission(USE_PERMISSION);
        boolean isAdmin = sender.hasPermission(ADMIN_PERMISSION);

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (canUse || isAdmin) {
                options.add("status");
                options.add("toggle");
                options.add("mode");
                options.add("open");
                options.add("close");
            }
            if (isAdmin) {
                options.add("editor");
                options.add("profile");
                options.add("filter");
                options.add("reload");
            }
            return filterPrefix(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "mode" -> (canUse || isAdmin)
                    ? filterPrefix(List.of("first", "third", "front", "auto"), args[1])
                    : List.of();
                case "open" -> (canUse || isAdmin)
                    ? filterPrefix(List.of("player", "ender", "crafting"), args[1])
                    : List.of();
                case "editor" -> {
                    if (!isAdmin) {
                        yield List.of();
                    }
                    List<String> opts = new ArrayList<>(List.of("menu", "mode", "set", "adjust", "save", "exit", "discard", "reset"));
                    opts.addAll(allScreenNames());
                    yield filterPrefix(opts, args[1]);
                }
                case "profile" -> isAdmin
                    ? filterPrefix(List.of("list", "info", "reset", "mode"), args[1])
                    : List.of();
                case "filter" -> isAdmin
                    ? filterPrefix(List.of("mode", "add", "remove", "list"), args[1])
                    : List.of();
                default -> List.of();
            };
        }

        if (args.length == 3) {
            String second = args[1].toLowerCase(Locale.ROOT);
            if ("editor".equals(sub) && isAdmin) {
                return switch (second) {
                    case "mode" -> filterPrefix(List.of("first", "third", "front"), args[2]);
                    case "set", "adjust" -> filterPrefix(EditorParameters.PARAMETER_NAMES, args[2]);
                    case "exit" -> filterPrefix(List.of("save", "discard"), args[2]);
                    default -> List.of();
                };
            }
            if ("profile".equals(sub) && isAdmin) {
                return switch (second) {
                    case "info", "reset", "mode" -> filterPrefix(allScreenNames(), args[2]);
                    default -> List.of();
                };
            }
            if ("filter".equals(sub) && isAdmin) {
                return switch (second) {
                    case "mode" -> filterPrefix(List.of("whitelist", "blacklist"), args[2]);
                    case "add" -> {
                        Set<ScreenType> current = plugin.getConfigManager().getFilteredScreens();
                        List<String> available = new ArrayList<>();
                        for (ScreenType type : ScreenType.values()) {
                            if (!current.contains(type)) {
                                available.add(type.name());
                            }
                        }
                        yield filterPrefix(available, args[2]);
                    }
                    case "remove" -> {
                        List<String> existing = new ArrayList<>();
                        for (ScreenType type : plugin.getConfigManager().getFilteredScreens()) {
                            existing.add(type.name());
                        }
                        yield filterPrefix(existing, args[2]);
                    }
                    default -> List.of();
                };
            }
        }

        if (args.length == 4) {
            String second = args[1].toLowerCase(Locale.ROOT);
            if ("editor".equals(sub) && isAdmin) {
                String canonical = EditorParameters.normalizeParameterName(args[2]);
                if ("set".equals(second) && canonical != null) {
                    List<String> suggestions = new ArrayList<>();
                    if (sender instanceof Player player && plugin.getEditorController() instanceof VisualEditorManager visual) {
                        ScreenProfile working = visual.getWorkingProfile(player.getUniqueId());
                        if (working != null) {
                            double cur = EditorParameters.getParameterValue(working, visual.getPreviewMode(player.getUniqueId()), canonical);
                            suggestions.add(formatNumber(cur));
                        }
                    }
                    suggestions.add("0");
                    suggestions.add("1.0");
                    return filterPrefix(suggestions, args[3]);
                }
                if ("adjust".equals(second) && canonical != null) {
                    boolean angular = isAngularParam(canonical);
                    boolean ticks = "open-ticks".equals(canonical) || "close-ticks".equals(canonical);
                    List<String> deltas = ticks
                        ? List.of("-5", "-1", "1", "5")
                        : (angular ? List.of("-15", "-5", "5", "15") : List.of("-0.2", "-0.05", "0.05", "0.2"));
                    return filterPrefix(deltas, args[3]);
                }
            }
            if ("profile".equals(sub) && "mode".equals(second) && isAdmin) {
                return filterPrefix(List.of("auto", "first", "third", "front", "disabled"), args[3]);
            }
        }

        return List.of();
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission(USE_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(prefixed("You lack permission (" + USE_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }

        ConfigManager config = plugin.getConfigManager();
        sender.sendMessage(
            Component.text("=== CookieTPS Status ===", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        sender.sendMessage(
            Component.text("Plugin: ", NamedTextColor.GRAY)
                .append(Component.text(config.isPluginEnabled() ? "ENABLED" : "DISABLED",
                    config.isPluginEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text(" | Default Mode: ", NamedTextColor.GRAY))
                .append(Component.text(config.getDefaultViewMode().name(), NamedTextColor.AQUA))
        );

        String filterLabel = config.isScreenFilterWhitelistMode() ? "WHITELIST" : "BLACKLIST";
        List<String> allowed = new ArrayList<>();
        for (ScreenType type : ScreenType.values()) {
            if (config.isScreenAllowed(type)) {
                allowed.add(type.name());
            }
        }
        sender.sendMessage(
            Component.text("Filter (" + filterLabel + "): ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", allowed), NamedTextColor.WHITE))
        );

        if (sender instanceof Player player) {
            PlayerPreferences prefs = plugin.getPreferencesStore().load(player.getUniqueId());
            SpatialSession session = plugin.getInputHandler() != null
                ? plugin.getInputHandler().getSession(player.getUniqueId())
                : null;
            String sessionText = session != null
                ? session.getScreenType().name() + " (" + session.getActiveMode().name() + ")"
                : "None";

            sender.sendMessage(
                Component.text("Your Spatial GUI: ", NamedTextColor.GRAY)
                    .append(Component.text(prefs.enabled() ? "ON" : "OFF",
                        prefs.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text(" | Preferred: ", NamedTextColor.GRAY))
                    .append(Component.text(prefs.preferredMode().name(), NamedTextColor.YELLOW))
                    .append(Component.text(" | Session: ", NamedTextColor.GRAY))
                    .append(Component.text(sessionText, NamedTextColor.AQUA))
            );

            sender.sendMessage(
                Component.text("Inventory Hotkey: ", NamedTextColor.GRAY)
                    .append(Component.text("[Shift + F]", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" or ", NamedTextColor.GRAY))
                    .append(Component.text("/ctps open", NamedTextColor.AQUA))
                    .append(Component.text(" (vanilla E is client-side; chests/crafting open automatically)", NamedTextColor.GRAY))
            );

            Component quickActions = Component.text("Quick: ", NamedTextColor.GRAY)
                .append(actionButton("[Toggle]", NamedTextColor.YELLOW, "/cookietps toggle", "Toggle 3D Spatial GUI on/off"))
                .append(Component.space())
                .append(actionButton("[Auto]", NamedTextColor.AQUA, "/cookietps mode auto", "Use screen profile default mode"))
                .append(Component.space())
                .append(actionButton("[First]", NamedTextColor.GREEN, "/cookietps mode first", "Prefer First-Person 3D GUI"))
                .append(Component.space())
                .append(actionButton("[Third]", NamedTextColor.LIGHT_PURPLE, "/cookietps mode third", "Prefer Third-Person (Back) camera GUI"))
                .append(Component.space())
                .append(actionButton("[Front]", NamedTextColor.BLUE, "/cookietps mode front", "Prefer Third-Person (Front) avatar-facing GUI"))
                .append(Component.space())
                .append(actionButton("[Inv]", NamedTextColor.WHITE, "/cookietps open player", "Open Player Inventory in 3D"))
                .append(Component.space())
                .append(actionButton("[Ender]", NamedTextColor.DARK_PURPLE, "/cookietps open ender", "Open Ender Chest in 3D"))
                .append(Component.space())
                .append(actionButton("[Craft]", NamedTextColor.GOLD, "/cookietps open crafting", "Open Crafting Table in 3D"));

            if (player.hasPermission(ADMIN_PERMISSION)) {
                quickActions = quickActions.append(Component.space())
                    .append(actionButton("[Editor]", NamedTextColor.GOLD, "/cookietps editor", "Open Visual Profile Editor"));
            }
            sender.sendMessage(quickActions);
        }
        return true;
    }

    private boolean handleToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed("Only players can toggle personal Spatial GUI preferences.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(prefixed("You lack permission (" + USE_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }

        PlayerPreferencesStore store = plugin.getPreferencesStore();
        PlayerPreferences current = store.load(player.getUniqueId());
        PlayerPreferences updated = current.withEnabled(!current.enabled());
        store.save(updated);

        if (!updated.enabled() && plugin.getInputHandler() != null) {
            SpatialSession active = plugin.getInputHandler().getSession(player.getUniqueId());
            if (active != null && !active.isEditorPreview()) {
                plugin.getInputHandler().closeSession(player, false);
            }
        }

        player.sendMessage(prefixed(
            "Spatial GUI is now " + (updated.enabled() ? "ENABLED" : "DISABLED") + " for you.",
            updated.enabled() ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));
        return true;
    }

    private boolean handleMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed("Only players can set their personal view mode.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(prefixed("You lack permission (" + USE_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(prefixed("Usage: /cookietps mode <first|third|front|auto>", NamedTextColor.YELLOW));
            return true;
        }

        ViewMode parsed = parsePlayerViewMode(args[1]);
        if (parsed == null) {
            player.sendMessage(prefixed("Invalid mode '" + args[1] + "'. Use: first, third, front, or auto.", NamedTextColor.RED));
            return true;
        }

        PlayerPreferencesStore store = plugin.getPreferencesStore();
        PlayerPreferences current = store.load(player.getUniqueId());
        store.save(current.withPreferredMode(parsed));
        player.sendMessage(prefixed(
            "Preferred Spatial GUI mode set to " + parsed.name() + ". Press [Shift + F] or run /ctps open for Player Inventory (vanilla E is client-side).",
            NamedTextColor.AQUA
        ));
        return true;
    }

    private boolean handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed("Only players can open a 3D Spatial screen.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(prefixed("You lack permission (" + USE_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }

        AdminEditorController editor = plugin.getEditorController();
        if (editor != null && editor.isEditing(player.getUniqueId())) {
            player.sendMessage(prefixed("Exit the Visual Editor before opening a regular Spatial screen.", NamedTextColor.YELLOW));
            return true;
        }

        ConfigManager config = plugin.getConfigManager();
        if (!config.isPluginEnabled()) {
            player.sendMessage(prefixed("CookieTPS is currently disabled by the server configuration.", NamedTextColor.RED));
            return true;
        }

        PlayerPreferences prefs = plugin.getPreferencesStore().load(player.getUniqueId());
        if (!prefs.enabled()) {
            player.sendMessage(prefixed("Your Spatial GUI is disabled. Run /cookietps toggle to enable it.", NamedTextColor.YELLOW));
            return true;
        }

        String target = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "player";
        ScreenType screenType;
        Inventory inventory;
        switch (target) {
            case "player", "inv", "inventory" -> {
                screenType = ScreenType.PLAYER_INVENTORY;
                inventory = player.getInventory();
            }
            case "ender", "ec", "enderchest", "ender_chest" -> {
                screenType = ScreenType.ENDER_CHEST;
                inventory = player.getEnderChest();
            }
            case "craft", "crafting", "workbench" -> {
                screenType = ScreenType.CRAFTING;
                inventory = Bukkit.createInventory(player, InventoryType.WORKBENCH);
            }
            default -> {
                player.sendMessage(prefixed("Usage: /cookietps open [player|ender|crafting]", NamedTextColor.YELLOW));
                return true;
            }
        }

        if (!config.isScreenAllowed(screenType)) {
            player.sendMessage(prefixed("Screen type " + screenType.name() + " is filtered out by server config.", NamedTextColor.RED));
            return true;
        }

        ScreenProfile profile = plugin.getProfileManager().getProfile(screenType);
        if (!profile.enabled() || profile.viewMode() == ViewMode.DISABLED) {
            player.sendMessage(prefixed("Screen type " + screenType.name() + " is disabled in profiles.yml.", NamedTextColor.RED));
            return true;
        }

        InputHandler inputHandler = plugin.getInputHandler();
        if (inputHandler == null) {
            player.sendMessage(prefixed("Input handler is not available.", NamedTextColor.RED));
            return true;
        }
        inputHandler.startSession(player, inventory, screenType, false);
        return true;
    }

    private boolean handleClose(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefixed("Only players can close an active Spatial screen.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(prefixed("You lack permission (" + USE_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }

        AdminEditorController editor = plugin.getEditorController();
        if (editor != null && editor.isEditing(player.getUniqueId())) {
            player.sendMessage(prefixed("You are in the Visual Editor. Use /cookietps editor save or /cookietps editor exit.", NamedTextColor.YELLOW));
            return true;
        }

        InputHandler inputHandler = plugin.getInputHandler();
        if (inputHandler != null && inputHandler.hasActiveSession(player.getUniqueId())) {
            inputHandler.closeSession(player, false);
            player.sendMessage(prefixed("Closed active Spatial GUI session.", NamedTextColor.AQUA));
        } else {
            player.sendMessage(prefixed("You do not have an active Spatial GUI session.", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean handleEditor(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(prefixed("You lack permission (" + ADMIN_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(prefixed("The Visual Editor must be used in-game by a player.", NamedTextColor.RED));
            return true;
        }

        AdminEditorController controller = plugin.getEditorController();
        if (controller == null) {
            admin.sendMessage(prefixed("Visual Editor controller is unavailable.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1) {
            ScreenType current = controller instanceof VisualEditorManager visual && visual.isEditing(admin.getUniqueId())
                ? visual.getEditingScreenType(admin.getUniqueId())
                : ScreenType.CHEST;
            controller.startEditor(admin, current);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "menu" -> {
                controller.openQuickSettingsMenu(admin);
                return true;
            }
            case "mode" -> {
                if (args.length < 3) {
                    admin.sendMessage(prefixed("Usage: /cookietps editor mode <first|third|front>", NamedTextColor.YELLOW));
                    return true;
                }
                ViewMode mode = parsePreviewMode(args[2]);
                if (mode == null) {
                    admin.sendMessage(prefixed("Invalid preview mode '" + args[2] + "'. Use: first, third, or front.", NamedTextColor.RED));
                    return true;
                }
                controller.switchPreviewMode(admin, mode);
                return true;
            }
            case "set" -> {
                if (args.length < 4) {
                    admin.sendMessage(prefixed("Usage: /cookietps editor set <param> <value>", NamedTextColor.YELLOW));
                    return true;
                }
                Double value = parseFiniteDouble(args[3]);
                if (value == null) {
                    admin.sendMessage(prefixed("Invalid number: " + args[3], NamedTextColor.RED));
                    return true;
                }
                if (controller instanceof VisualEditorManager visual) {
                    visual.setParameter(admin, args[2], value);
                } else {
                    admin.sendMessage(prefixed("VisualEditorManager is not active.", NamedTextColor.RED));
                }
                return true;
            }
            case "adjust" -> {
                if (args.length < 4) {
                    admin.sendMessage(prefixed("Usage: /cookietps editor adjust <param> <delta>", NamedTextColor.YELLOW));
                    return true;
                }
                Double delta = parseFiniteDouble(args[3]);
                if (delta == null) {
                    admin.sendMessage(prefixed("Invalid number: " + args[3], NamedTextColor.RED));
                    return true;
                }
                if (controller instanceof VisualEditorManager visual) {
                    visual.adjustParameter(admin, args[2], delta);
                } else {
                    admin.sendMessage(prefixed("VisualEditorManager is not active.", NamedTextColor.RED));
                }
                return true;
            }
            case "save" -> {
                controller.saveActiveProfile(admin);
                return true;
            }
            case "exit" -> {
                boolean save = args.length >= 3 && "save".equalsIgnoreCase(args[2]);
                controller.exitEditor(admin, save);
                return true;
            }
            case "discard" -> {
                controller.exitEditor(admin, false);
                return true;
            }
            case "reset" -> {
                controller.resetActiveProfile(admin);
                return true;
            }
            default -> {
                ScreenType screenType = parseScreenType(args[1]);
                if (screenType == null) {
                    admin.sendMessage(prefixed("Unknown editor action or screen type: " + args[1], NamedTextColor.RED));
                    return true;
                }
                controller.startEditor(admin, screenType);
                return true;
            }
        }
    }

    private boolean handleProfile(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(prefixed("You lack permission (" + ADMIN_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(prefixed("Usage: /cookietps profile <list|info|reset|mode> ...", NamedTextColor.YELLOW));
            return true;
        }

        ProfileManager profiles = plugin.getProfileManager();
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                sender.sendMessage(Component.text("=== Screen Profiles ===", NamedTextColor.GOLD, TextDecoration.BOLD));
                for (ScreenType type : ScreenType.values()) {
                    ScreenProfile p = profiles.getProfile(type);
                    Component line = Component.text("- " + type.name(), NamedTextColor.AQUA)
                        .append(Component.text(" [" + (p.enabled() ? "ON" : "OFF") + " | " + p.viewMode().name() + "]",
                            p.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .append(Component.text(" FP(d=" + formatNumber(p.firstPersonTransform().distance())
                            + ", s=" + formatNumber(p.firstPersonTransform().scale())
                            + ", c=" + formatNumber(p.firstPersonTransform().curvature()) + ")", NamedTextColor.GRAY));
                    if (sender instanceof Player) {
                        line = line.append(Component.space())
                            .append(actionButton("[Edit]", NamedTextColor.GOLD, "/cookietps editor " + type.name(), "Open in Visual Editor"))
                            .append(Component.space())
                            .append(actionButton("[Info]", NamedTextColor.YELLOW, "/cookietps profile info " + type.name(), "Inspect profile details"));
                    }
                    sender.sendMessage(line);
                }
                return true;
            }
            case "info" -> {
                if (args.length < 3) {
                    sender.sendMessage(prefixed("Usage: /cookietps profile info <screenType>", NamedTextColor.YELLOW));
                    return true;
                }
                ScreenType type = parseScreenType(args[2]);
                if (type == null) {
                    sender.sendMessage(prefixed("Unknown screen type: " + args[2], NamedTextColor.RED));
                    return true;
                }
                sendProfileDetails(sender, profiles.getProfile(type));
                return true;
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(prefixed("Usage: /cookietps profile reset <screenType>", NamedTextColor.YELLOW));
                    return true;
                }
                ScreenType type = parseScreenType(args[2]);
                if (type == null) {
                    sender.sendMessage(prefixed("Unknown screen type: " + args[2], NamedTextColor.RED));
                    return true;
                }
                profiles.resetProfile(type);
                sender.sendMessage(prefixed("Reset profile " + type.name() + " to defaults.", NamedTextColor.GREEN));
                return true;
            }
            case "mode" -> {
                if (args.length < 4) {
                    sender.sendMessage(prefixed("Usage: /cookietps profile mode <screenType> <auto|first|third|front|disabled>", NamedTextColor.YELLOW));
                    return true;
                }
                ScreenType type = parseScreenType(args[2]);
                if (type == null) {
                    sender.sendMessage(prefixed("Unknown screen type: " + args[2], NamedTextColor.RED));
                    return true;
                }
                ViewMode mode = parseProfileViewMode(args[3]);
                if (mode == null) {
                    sender.sendMessage(prefixed("Invalid mode '" + args[3] + "'. Use: auto, first, third, front, or disabled.", NamedTextColor.RED));
                    return true;
                }
                ScreenProfile updated = profiles.getProfile(type).withViewMode(mode);
                profiles.saveProfile(updated);
                sender.sendMessage(prefixed("Set " + type.name() + " view-mode to " + mode.name() + ".", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                ScreenType directType = parseScreenType(args[1]);
                if (directType != null) {
                    sendProfileDetails(sender, profiles.getProfile(directType));
                    return true;
                }
                sender.sendMessage(prefixed("Usage: /cookietps profile <list|info|reset|mode> ...", NamedTextColor.YELLOW));
                return true;
            }
        }
    }

    private boolean handleFilter(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(prefixed("You lack permission (" + ADMIN_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(prefixed("Usage: /cookietps filter <mode|add|remove|list> ...", NamedTextColor.YELLOW));
            return true;
        }

        ConfigManager config = plugin.getConfigManager();
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "mode" -> {
                if (args.length < 3) {
                    sender.sendMessage(prefixed("Usage: /cookietps filter mode <whitelist|blacklist>", NamedTextColor.YELLOW));
                    return true;
                }
                String modeRaw = args[2].toLowerCase(Locale.ROOT);
                if (!"whitelist".equals(modeRaw) && !"blacklist".equals(modeRaw)) {
                    sender.sendMessage(prefixed("Filter mode must be 'whitelist' or 'blacklist'.", NamedTextColor.RED));
                    return true;
                }
                boolean whitelist = "whitelist".equals(modeRaw);
                config.setScreenFilterWhitelistMode(whitelist);
                sender.sendMessage(prefixed("Screen filter mode set to " + (whitelist ? "WHITELIST" : "BLACKLIST") + ".", NamedTextColor.GREEN));
                return true;
            }
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(prefixed("Usage: /cookietps filter add <screenType>", NamedTextColor.YELLOW));
                    return true;
                }
                ScreenType type = parseScreenType(args[2]);
                if (type == null) {
                    sender.sendMessage(prefixed("Unknown screen type: " + args[2], NamedTextColor.RED));
                    return true;
                }
                config.addFilteredScreen(type);
                sender.sendMessage(prefixed("Added " + type.name() + " to screen filter list.", NamedTextColor.GREEN));
                return true;
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(prefixed("Usage: /cookietps filter remove <screenType>", NamedTextColor.YELLOW));
                    return true;
                }
                ScreenType type = parseScreenType(args[2]);
                if (type == null) {
                    sender.sendMessage(prefixed("Unknown screen type: " + args[2], NamedTextColor.RED));
                    return true;
                }
                config.removeFilteredScreen(type);
                sender.sendMessage(prefixed("Removed " + type.name() + " from screen filter list.", NamedTextColor.GREEN));
                return true;
            }
            case "list" -> {
                String mode = config.isScreenFilterWhitelistMode() ? "WHITELIST" : "BLACKLIST";
                Set<ScreenType> listed = config.getFilteredScreens();
                sender.sendMessage(
                    Component.text("Screen Filter Mode: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text(mode, NamedTextColor.AQUA))
                );
                sender.sendMessage(
                    Component.text("Listed Screens: ", NamedTextColor.GRAY)
                        .append(Component.text(listed.isEmpty() ? "(none)" : joinScreenNames(listed), NamedTextColor.WHITE))
                );
                List<String> allowed = new ArrayList<>();
                for (ScreenType type : ScreenType.values()) {
                    if (config.isScreenAllowed(type)) {
                        allowed.add(type.name());
                    }
                }
                sender.sendMessage(
                    Component.text("Currently Allowed: ", NamedTextColor.GRAY)
                        .append(Component.text(allowed.isEmpty() ? "(none)" : String.join(", ", allowed), NamedTextColor.GREEN))
                );
                return true;
            }
            default -> {
                sender.sendMessage(prefixed("Usage: /cookietps filter <mode|add|remove|list> ...", NamedTextColor.YELLOW));
                return true;
            }
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(prefixed("You lack permission (" + ADMIN_PERMISSION + ").", NamedTextColor.RED));
            return true;
        }
        plugin.getConfigManager().reload();
        plugin.getProfileManager().reload();
        sender.sendMessage(prefixed("Reloaded config.yml and profiles.yml.", NamedTextColor.GREEN));
        return true;
    }

    private void sendProfileDetails(CommandSender sender, ScreenProfile p) {
        RenderTransform fp = p.firstPersonTransform();
        RenderTransform tp = p.thirdPersonTransform();
        ThirdPersonSettings tps = p.thirdPersonSettings();
        sender.sendMessage(
            Component.text("=== Profile: " + p.screenType().name() + " ===", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        sender.sendMessage(Component.text(
            String.format(
                Locale.ROOT,
                "Enabled: %s | Mode: %s | Grid: %dx%d | Spacing: %.2f | Bg: %s | Tips: %s | PlayerInv: %s",
                p.enabled(),
                p.viewMode().name(),
                p.columns(),
                p.rows(),
                p.slotSpacing(),
                p.showBackground(),
                p.showTooltips(),
                p.includePlayerInventory()
            ),
            NamedTextColor.WHITE
        ));
        sender.sendMessage(Component.text(
            String.format(
                Locale.ROOT,
                "FP: dist=%.2f scale=%.2f curv=%.2f off=(%.2f,%.2f,%.2f) rot=(p=%.1f,y=%.1f,r=%.1f)",
                fp.distance(),
                fp.scale(),
                fp.curvature(),
                fp.offsetX(),
                fp.offsetY(),
                fp.offsetZ(),
                fp.pitchOffset(),
                fp.yawOffset(),
                fp.rollOffset()
            ),
            NamedTextColor.AQUA
        ));
        sender.sendMessage(Component.text(
            String.format(
                Locale.ROOT,
                "TP: dist=%.2f scale=%.2f curv=%.2f off=(%.2f,%.2f,%.2f) rot=(p=%.1f,y=%.1f,r=%.1f)",
                tp.distance(),
                tp.scale(),
                tp.curvature(),
                tp.offsetX(),
                tp.offsetY(),
                tp.offsetZ(),
                tp.pitchOffset(),
                tp.yawOffset(),
                tp.rollOffset()
            ),
            NamedTextColor.LIGHT_PURPLE
        ));
        sender.sendMessage(Component.text(
            String.format(
                Locale.ROOT,
                "TP Cam: dist=%.2f height=%.2f side=%.2f yaw=%.1f pitch=%.1f minClear=%.2f glide=(%d/%d) headTrack=%s wallFallback=%s",
                tps.cameraDistance(),
                tps.cameraHeightOffset(),
                tps.cameraSideOffset(),
                tps.cameraYawOffset(),
                tps.cameraPitchOffset(),
                tps.minClearance(),
                tps.openTransitionTicks(),
                tps.closeTransitionTicks(),
                tps.headTrackHoveredSlot(),
                tps.fallbackToFirstPersonOnOcclusion()
            ),
            NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
            String.format(
                Locale.ROOT,
                "TP Front Cam: dist=%.2f height=%.2f side=%.2f yaw=%.1f pitch=%.1f",
                tps.frontCameraDistance(),
                tps.frontCameraHeightOffset(),
                tps.frontCameraSideOffset(),
                tps.frontCameraYawOffset(),
                tps.frontCameraPitchOffset()
            ),
            NamedTextColor.GRAY
        ));
    }

    private static Component actionButton(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private static Component prefixed(String message, NamedTextColor color) {
        return Component.text("[CookieTPS] ", NamedTextColor.GOLD)
            .append(Component.text(message, color));
    }

    private static ViewMode parsePlayerViewMode(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(key)) {
            return ViewMode.AUTO;
        }
        return parsePreviewMode(key);
    }

    private static ViewMode parsePreviewMode(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "first", "fp", "first_person", "first-person" -> ViewMode.FIRST_PERSON;
            case "2", "third", "tp", "third_person", "third-person", "back" -> ViewMode.THIRD_PERSON;
            case "3", "front", "tp-front", "tp_front", "third_person_front", "third-person-front" -> ViewMode.THIRD_PERSON_FRONT;
            default -> null;
        };
    }

    private static ViewMode parseProfileViewMode(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(key)) {
            return ViewMode.AUTO;
        }
        if ("disabled".equals(key) || "off".equals(key)) {
            return ViewMode.DISABLED;
        }
        return parsePreviewMode(key);
    }

    private static boolean isAngularParam(String canonical) {
        return "yaw".equals(canonical)
            || "pitch".equals(canonical)
            || "roll".equals(canonical)
            || "cam-yaw".equals(canonical)
            || "cam-pitch".equals(canonical)
            || "front-cam-yaw".equals(canonical)
            || "front-cam-pitch".equals(canonical);
    }

    private static ScreenType parseScreenType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ScreenType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Double parseFiniteDouble(String raw) {
        try {
            double parsed = Double.parseDouble(raw);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> allScreenNames() {
        List<String> names = new ArrayList<>();
        for (ScreenType type : ScreenType.values()) {
            names.add(type.name());
        }
        return names;
    }

    private static String joinScreenNames(Set<ScreenType> set) {
        List<String> names = new ArrayList<>(set.size());
        for (ScreenType type : set) {
            names.add(type.name());
        }
        return String.join(", ", names);
    }

    private static List<String> filterPrefix(List<String> candidates, String prefix) {
        String lower = prefix != null ? prefix.toLowerCase(Locale.ROOT) : "";
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
