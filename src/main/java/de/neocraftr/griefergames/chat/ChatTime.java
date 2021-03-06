package de.neocraftr.griefergames.chat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import de.neocraftr.griefergames.settings.ModSettings;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public class ChatTime extends Chat {
	private IChatComponent resetMsg = new ChatComponentText(" ").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RESET));
	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

	@Override
	public String getName() {
		return "chatTime";
	}

	@Override
	public boolean doActionModifyChatMessage(IChatComponent msg) {
		String unformatted = msg.getUnformattedText();

		return unformatted.trim().length() > 0 && getSettings().isShowChatTime() && getSettings().getChatTimeFormat().trim().length() > 0;
	}

	@Override
	public IChatComponent modifyChatMessage(IChatComponent msg) {
		String[] time = LocalDateTime.now().format(formatter).split(":");

		String timeMsg = getSettings().getChatTimeFormat();
		if(timeMsg.trim().length() == 0) {
			timeMsg = ModSettings.DEFAULT_CHATTIME_FORMAT;
		}
		timeMsg = getHelper().colorize(timeMsg);
		timeMsg = timeMsg.replace("%h%", time[0]);
		timeMsg = timeMsg.replace("%m%", time[1]);
		timeMsg = timeMsg.replace("%s%", time[2]);

		if(getSettings().isChatTimeAfterMessage()) {
			msg.getSiblings().add(resetMsg);
			msg.getSiblings().add(new ChatComponentText(timeMsg));
		} else {
			msg.getSiblings().add(0, new ChatComponentText(timeMsg));
			msg.getSiblings().add(1, resetMsg);
		}
		return msg;
	}
}
