package jp.me1han.sam;

import jp.me1han.sam.network.NetworkHandler;
import jp.me1han.sam.network.PacketAnnounce;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;

public class CommandSAM extends CommandBase {
    @Override
    public String getCommandName() {
        return "sam";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/sam stopall";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("stopall")) {
            NetworkHandler.INSTANCE.sendToAll(new PacketAnnounce(true, null));
            sender.addChatMessage(new ChatComponentText("§a[SAM] All sound stopped"));
        } else {
            sender.addChatMessage(new ChatComponentText("§c使用法: " + getCommandUsage(sender)));
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            options.add("stopall");
            return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
        }
        return null;
    }
}
