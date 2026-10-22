/*
 * @author     ucchy
 * @license    LGPLv3
 * @copyright  Copyright ucchy 2020
 */
package com.github.ucchyocean.lc3.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.github.ucchyocean.lc3.LunaChat;
import com.github.ucchyocean.lc3.LunaChatAPI;
import com.github.ucchyocean.lc3.Messages;
import com.github.ucchyocean.lc3.channel.Channel;
import com.github.ucchyocean.lc3.member.ChannelMember;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * チャットのフォーマットを作成するユーティリティクラス
 * @author ucchy
 */
public class ClickableFormat {

    private static final String JOIN_COMMAND_TEMPLATE = "/lunachat join %s";
    private static final String TELL_COMMAND_TEMPLATE = "/tell %s";

    // Only plugin-created parts may carry events. Never parse events from chat text.
    private List<MessagePart> parts = new ArrayList<>();

    private static class MessagePart {
        private final KeywordReplacer text;
        private final ClickEvent click;
        private final String hover;

        private MessagePart(String text, ClickEvent click, String hover) {
            this.text = new KeywordReplacer(text);
            this.click = click;
            this.hover = hover;
        }
    }

    private ClickableFormat(String message) {
        parts.add(new MessagePart(message, null, null));
    }

    /**
     * チャットフォーマット内のキーワードを置き換えする
     * @param format チャットフォーマット
     * @param member 発言者
     * @return 置き換え結果
     */
    public static ClickableFormat makeFormat(String format, @Nullable ChannelMember member) {
        return makeFormat(format, member, null, true);
    }

    /**
     * チャットフォーマット内のキーワードを置き換えする
     * @param format チャットフォーマット
     * @param member 発言者
     * @param channel チャンネル
     * @param withPlayerLink プレイヤー名の箇所にクリック可能なプレースホルダーを挿入するか
     * @return 置き換え結果
     */
    public static ClickableFormat makeFormat(String format,
            @Nullable ChannelMember member, @Nullable Channel channel, boolean withPlayerLink) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

        LunaChatAPI api = LunaChat.getAPI();

        ClickableFormat msg = new ClickableFormat(format);

        if ( channel != null ) {

            // テンプレートのキーワードを、まず最初に置き換える
            for ( int i=0; i<=9; i++ ) {
                String key = "%" + i;
                if ( msg.contains(key) ) {
                    if ( api.getTemplate("" + i) != null ) {
                        msg.replace(key, api.getTemplate("" + i));
                        break;
                    }
                }
            }

            // チャンネル関連のキーワード置き換え
            msg.replaceClickable("%ch", ClickEvent.Action.RUN_COMMAND,
                    channel.getName(),
                    Messages.hoverChannelName(channel.getName()),
                    String.format(JOIN_COMMAND_TEMPLATE, channel.getName()));
            msg.replace("%color", channel.getColorCode());
            if ( channel.getPrivateMessageTo() != null ) {
                msg.replaceClickable("%to", ClickEvent.Action.SUGGEST_COMMAND,
                        channel.getPrivateMessageTo().getDisplayName(),
                        Messages.hoverPlayerName(channel.getPrivateMessageTo().getName()),
                        String.format(TELL_COMMAND_TEMPLATE, channel.getPrivateMessageTo().getName()));
                msg.replace("%recieverserver", channel.getPrivateMessageTo().getServerName());
            }
        }

        if ( msg.contains("%date") ) {
            msg.replace("%date", dateFormat.format(new Date()));
        }
        if ( msg.contains("%time") ) {
            msg.replace("%time", timeFormat.format(new Date()));
        }

        if ( member != null ) {

            // ChannelMember関連のキーワード置き換え
            if ( withPlayerLink ) {
                msg.replaceClickable("%displayname", ClickEvent.Action.SUGGEST_COMMAND,
                        member.getDisplayName(), Messages.hoverPlayerName(member.getName()),
                        String.format(TELL_COMMAND_TEMPLATE, member.getName()));
                msg.replaceClickable("%username", ClickEvent.Action.SUGGEST_COMMAND,
                        member.getDisplayName(), Messages.hoverPlayerName(member.getName()),
                        String.format(TELL_COMMAND_TEMPLATE, member.getName()));
                msg.replaceClickable("%player", ClickEvent.Action.SUGGEST_COMMAND,
                        member.getName(), Messages.hoverPlayerName(member.getName()),
                        String.format(TELL_COMMAND_TEMPLATE, member.getName()));
            } else {
                msg.replace("%displayname", member.getDisplayName());
                msg.replace("%username", member.getDisplayName());
                msg.replace("%player", member.getName());
            }

            if ( msg.contains("%prefix") || msg.contains("%suffix") ) {
                msg.replace("%prefix", member.getPrefix());
                msg.replace("%suffix", member.getSuffix());
            }

            msg.replace("%world", member.getWorldName());
            msg.replace("%server", member.getServerName());
        }

        return msg;
    }

    /**
     * チャンネルチャットのメッセージ用のフォーマットを置き換えする
     * @param format フォーマット
     * @param channelName チャンネル名
     * @return 置き換え結果
     */
    public static ClickableFormat makeChannelClickableMessage(String format, String channelName) {

        ClickableFormat msg = new ClickableFormat(format);
        String stripped = Utility.stripColorCode(channelName);
        msg.replaceClickable("%channel%", ClickEvent.Action.RUN_COMMAND,
                channelName,
                Messages.hoverChannelName(stripped),
                String.format(JOIN_COMMAND_TEMPLATE, stripped));

        return msg;
    }

    /**
     * チャットフォーマット内のキーワードをBukkitの通常チャットイベント用に置き換えする
     * @param format 置き換え元のチャットフォーマット
     * @param member 発言者
     * @return 置き換え結果
     */
    public static String replaceForNormalChatFormat(String format, ChannelMember member) {
        format = format.replace("%displayName", "%1$s");
        format = format.replace("%username", "%1$s");
        format = format.replace("%msg", "%2$s");
        return makeFormat(format, member, null, false).toLegacyText();
    }

    public BaseComponent[] makeTextComponent() {
        List<BaseComponent> components = new ArrayList<>();
        for ( MessagePart part : parts ) {
            part.text.translateColorCode();
            if ( part.text.length() == 0 ) {
                continue;
            }
            BaseComponent[] text = TextComponent.fromLegacyText(part.text.toString());
            if ( part.click == null ) {
                for ( BaseComponent component : text ) {
                    components.add(component);
                }
                continue;
            }

            TextComponent tc = new TextComponent(text);
            if ( !part.hover.isEmpty() ) {
                @SuppressWarnings("deprecation")
                HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(Utility.replaceColorCode(part.hover)).create());
                tc.setHoverEvent(event);
            }
            tc.setClickEvent(part.click);

            // Preserve the preceding color for channel/player links (issue #202).
            if ( !components.isEmpty() ) {
                tc.setColor(components.get(components.size() - 1).getColor());
            }
            components.add(tc);
        }
        return components.toArray(new BaseComponent[components.size()]);
    }

    public String toLegacyText() {
        StringBuilder result = new StringBuilder();
        for ( MessagePart part : parts ) {
            result.append(part.text.toString());
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return toLegacyText();
    }

    public void replace(String keyword, String value) {
        for ( MessagePart part : parts ) {
            if ( part.click == null ) {
                part.text.replace(keyword, value);
            }
        }
    }

    private boolean contains(String keyword) {
        for ( MessagePart part : parts ) {
            if ( part.click == null && part.text.contains(keyword) ) {
                return true;
            }
        }
        return false;
    }

    private void replaceClickable(String keyword, ClickEvent.Action action,
            String text, String hover, String command) {
        List<MessagePart> replaced = new ArrayList<>();
        for ( MessagePart part : parts ) {
            if ( part.click != null ) {
                replaced.add(part);
                continue;
            }
            String source = part.text.toString();
            int start = 0;
            int index;
            while ( (index = source.indexOf(keyword, start)) >= 0 ) {
                replaced.add(new MessagePart(source.substring(start, index), null, null));
                replaced.add(new MessagePart(text, new ClickEvent(action, command), hover));
                start = index + keyword.length();
            }
            replaced.add(new MessagePart(source.substring(start), null, null));
        }
        parts = replaced;
    }
}
