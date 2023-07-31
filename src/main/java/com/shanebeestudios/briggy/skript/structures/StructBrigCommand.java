package com.shanebeestudios.briggy.skript.structures;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.util.SimpleEvent;
import ch.njol.skript.util.Utils;
import com.shanebeestudios.briggy.api.BrigArgument;
import com.shanebeestudios.briggy.api.BrigCommand;
import com.shanebeestudios.briggy.api.event.BrigCommandCreateEvent;
import com.shanebeestudios.briggy.api.event.BrigCommandRunEvent;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.arguments.Argument;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.entry.EntryValidator;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.lang.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Name("Brig Command")
@Description({"Register a new Brigadier command.",
        "\nNotes:",
        "\nFormat: 'brig command /commandName <brigArgType> [<brigArgType(optional)>] <argName:brigArgType> [<argName:brigArgType(optional)>]:'",
        "\n`commandName` = the name of your command, ex: '/mycommand'.",
        "\n`brigArgType` = represents a brig argument type. While some may match Skript types, this doesn't actually support Skript types.",
        "\n`argName` = The name of the arg, which will be used to create a local variable for the arg.",
        "This will also show in some cases when typing out a command in game.",
        "If this isn't set a local variable will be created using the type (see examples).",
        "\nJust like Skript commands, wrapping your arg in `[]` makes it optional. Do note at this time there is no support for defaults."})
@Examples({"brig command /move <player> <location>:",
        "\ttrigger:",
        "\t\tteleport {_player} to {_location}",
        "",
        "brig command /move <p1:player> <p2:player>:",
        "\ttrigger:",
        "\t\tteleport {_p1} to {_p2}",
        "",
        "brig command /i <item> [<amount:int>]:",
        "\ttrigger:",
        "\t\tset {_amount} to 1 if {_amount} isn't set",
        "\t\tgive {_amount} of {_item} to player"})
@Since("INSERT VERSION")
public class StructBrigCommand extends Structure {

    static {
        EntryValidator entryValidator = EntryValidator.builder()
                .addEntry("permission", null, true)
                .addEntry("description", "SkBriggy Command", true)
                .addSection("arguments", true)
                .addSection("trigger", false)
                .build();
        Skript.registerStructure(StructBrigCommand.class, entryValidator, "brig[gy] command /<.+>");
    }

    private String command;
    private List<String> args;

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult, EntryContainer entryContainer) {
        String[] split = parseResult.regexes.get(0).group().split(" ");
        this.command = split[0];
        this.args = new ArrayList<>(Arrays.asList(split).subList(1, split.length));
        return true;
    }

    @Override
    public boolean load() {
        // Create Command
        Script currentScript = getParser().getCurrentScript();
        getParser().setCurrentEvent("brig command", BrigCommandCreateEvent.class);
        EntryContainer entryContainer = getEntryContainer();
        BrigCommand brigCommand = new BrigCommand(this.command);

        // Register command arguments
        if (!parseArgs(brigCommand)) {
            return false;
        }

        // Register section arguments
        SectionNode argNode = entryContainer.getOptional("arguments", SectionNode.class, false);
        if (argNode != null) {
            Trigger argTrigger = new Trigger(currentScript, "briggy command /" + this.command, new SimpleEvent(), ScriptLoader.loadItems(argNode));
            Trigger.walk(argTrigger, new BrigCommandCreateEvent(brigCommand));
        }

        // Register command permission
        String permission = entryContainer.getOptional("permission", String.class, false);
        brigCommand.setPermission(permission);

        // Register command description
        String description = entryContainer.getOptional("description", String.class, true);
        assert description != null;
        description = Utils.replaceEnglishChatStyles(description);
        brigCommand.setDescription(description);

        // Register command trigger
        getParser().setCurrentEvent("brig command", BrigCommandRunEvent.class);
        SectionNode triggerNode = entryContainer.get("trigger", SectionNode.class, false);
        Trigger trigger = new Trigger(currentScript, "briggy command /" + this.command, new SimpleEvent(), ScriptLoader.loadItems(triggerNode));
        trigger.setLineNumber(triggerNode.getLine());

        // Build command
        brigCommand.addExecution(trigger);
        brigCommand.build();

        getParser().deleteCurrentEvent();
        return true;
    }

    private boolean parseArgs(BrigCommand brigCommand) {
        boolean optional = false;
        for (String arg : this.args) {
            if (arg.startsWith("[") && arg.endsWith("]")) {
                optional = true;
                arg = arg.replace("[", "").replace("]", "");
            }
            if (arg.startsWith("<") && arg.endsWith(">")) {
                arg = arg.replace("<", "").replace(">", "");
            } else {
                Skript.error("Invalid placement of <> and/or [] in brig arg '" + arg + "'");
                return false;
            }

            String name;
            String type;
            if (arg.contains(":")) {
                String[] split = arg.split(":");
                name = split[0];
                type = split[1];
            } else {
                name = arg;
                type = arg;
            }
            BrigArgument brigArgument = BrigArgument.parse(type);
            if (brigArgument == null) {
                Skript.error("Invalid brig argument type '" + type + "'");
                return false;
            }

            Argument<?> argument = brigArgument.getArgument(name);
            argument.setOptional(optional);
            brigCommand.addArgument(name, argument);

        }
        return true;
    }

    @Override
    public void unload() {
        CommandAPI.unregister(this.command, true);
    }

    @Override
    public @NotNull String toString(@Nullable Event e, boolean d) {
        return "briggy command";
    }

}
