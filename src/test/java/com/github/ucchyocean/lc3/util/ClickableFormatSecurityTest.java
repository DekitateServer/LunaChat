package com.github.ucchyocean.lc3.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.github.ucchyocean.lc3.LunaChat;
import com.github.ucchyocean.lc3.LunaChatStandalone;
import com.github.ucchyocean.lc3.Messages;
import com.github.ucchyocean.lc3.channel.Channel;
import com.github.ucchyocean.lc3.channel.StandaloneChannel;
import com.github.ucchyocean.lc3.member.ChannelMember;
import com.github.ucchyocean.lc3.member.ChannelMemberDummy;

import junit.framework.TestCase;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.chat.ComponentSerializer;

public class ClickableFormatSecurityTest extends TestCase {

    private static final String PAYLOAD =
            "＜type=RUN_COMMAND text=\"click\" hover=\"hint\" command=\"/tp @s @s\"＞";

    @Override
    protected void setUp() {
        File data = new File("target/LunaChatClickableSecurityTest");
        data.mkdirs();
        new LunaChatStandalone(data).onEnable();
        Messages.initialize(new File("target/classes"), null, "ja");
    }

    public void testMessageMarkupIsLiteralForBothActions() {
        for ( String action : new String[] {"RUN_COMMAND", "SUGGEST_COMMAND"} ) {
            String payload = PAYLOAD.replace("RUN_COMMAND", action);
            ClickableFormat format = ClickableFormat.makeFormat("%msg", null);
            format.replace("%msg", payload);
            assertLiteral(format, payload);
        }
    }

    public void testChannelFormatCannotTurnMessageIntoCommand() {
        String source = PAYLOAD.replace("/tp @s @s", "%msg");
        ClickableFormat format = ClickableFormat.makeFormat(source, null, new StandaloneChannel("test"), true);
        format.replace("%msg", "/tp @s @s");
        assertLiteral(format, PAYLOAD);
    }

    public void testMarkupSplitBetweenFormatAndMessageIsLiteral() {
        int split = PAYLOAD.indexOf("text=");
        ClickableFormat format = ClickableFormat.makeFormat(PAYLOAD.substring(0, split) + "%msg", null);
        format.replace("%msg", PAYLOAD.substring(split));
        assertLiteral(format, PAYLOAD);
    }

    public void testTemplateMarkupIsLiteral() {
        LunaChat.getAPI().setTemplate("9", PAYLOAD + " %msg");
        ClickableFormat format = ClickableFormat.makeFormat("%9", null, new StandaloneChannel("test"), true);
        format.replace("%msg", "hello");
        assertLiteral(format, PAYLOAD + " hello");
    }

    public void testPrefixAndSuffixMarkupIsLiteral() {
        ChannelMember member = new ChannelMemberDummy() {
            @Override
            public String getPrefix() {
                return PAYLOAD;
            }

            @Override
            public String getSuffix() {
                return PAYLOAD;
            }
        };
        assertLiteral(ClickableFormat.makeFormat("%prefix%suffix", member), PAYLOAD + PAYLOAD);
    }

    public void testDisplayNameCannotBreakOutOfPlayerLink() {
        String displayName = "name\" hover=\"hint\" command=\"/tp @s @s\"＞" + PAYLOAD;
        ChannelMember member = new ChannelMemberDummy() {
            @Override
            public String getDisplayName() {
                return displayName;
            }
        };
        ClickableFormat format = ClickableFormat.makeFormat("%username", member);
        BaseComponent[] components = roundTrip(format);
        assertEquals(displayName, BaseComponent.toPlainText(components));
        assertEvents(components, ClickEvent.Action.SUGGEST_COMMAND, "/tell ucchy");
    }

    public void testMessageDoesNotReplaceGeneratedLinkFields() {
        ChannelMember member = new ChannelMemberDummy() {
            @Override
            public String getName() {
                return "external%msg";
            }
        };
        ClickableFormat format = ClickableFormat.makeFormat("%player %msg", member);
        format.replace("%msg", PAYLOAD);
        BaseComponent[] components = roundTrip(format);
        assertEquals("external%msg " + PAYLOAD, BaseComponent.toPlainText(components));
        assertEvents(components, ClickEvent.Action.SUGGEST_COMMAND, "/tell external%msg");
    }

    public void testStandardLinksSurviveAlongsideHostileMessage() {
        Channel channel = new StandaloneChannel("test");
        ClickableFormat format = ClickableFormat.makeFormat("&7[%ch] %username: %msg",
                new ChannelMemberDummy(), channel, true);
        format.replace("%msg", PAYLOAD);
        BaseComponent[] components = roundTrip(format);
        assertEquals("[test] うっちぃ: " + PAYLOAD, BaseComponent.toPlainText(components));
        List<ClickEvent> events = events(components);
        assertTrue(events.contains(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lunachat join test")));
        assertTrue(events.contains(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell ucchy")));
        for ( ClickEvent event : events ) {
            assertTrue(event.toString(), event.equals(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lunachat join test"))
                    || event.equals(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell ucchy")));
        }
        BaseComponent channelLink = format.makeTextComponent()[1];
        assertEquals(net.md_5.bungee.api.ChatColor.GRAY, channelLink.getColor());
        assertNotNull(channelLink.getHoverEvent());
    }

    public void testPrivateMessageRecipientLinkSurvives() {
        Channel channel = new StandaloneChannel("test");
        channel.setPrivateMessageTo(new ChannelMemberDummy());
        ClickableFormat format = ClickableFormat.makeFormat("%to %msg", null, channel, true);
        format.replace("%msg", PAYLOAD);
        BaseComponent[] components = roundTrip(format);
        assertEquals("うっちぃ " + PAYLOAD, BaseComponent.toPlainText(components));
        assertEvents(components, ClickEvent.Action.SUGGEST_COMMAND, "/tell ucchy");
    }

    public void testChannelNotificationDoesNotParseMarkupInOtherFields() {
        ClickableFormat format = ClickableFormat.makeChannelClickableMessage("%channel% %topic%", "&atest");
        format.replace("%topic%", PAYLOAD);
        BaseComponent[] components = roundTrip(format);
        assertEquals("test " + PAYLOAD, BaseComponent.toPlainText(components));
        assertEvents(components, ClickEvent.Action.RUN_COMMAND, "/lunachat join test");
    }

    public void testEmptyAndSingleCharacterMessagesAndLinkSuffix() {
        assertLiteral(ClickableFormat.makeFormat("", null), "");
        assertLiteral(ClickableFormat.makeFormat("x", null), "x");
        ClickableFormat format = ClickableFormat.makeFormat("%player!", new ChannelMemberDummy());
        assertEquals("ucchy!", BaseComponent.toPlainText(roundTrip(format)));
    }

    private static void assertLiteral(ClickableFormat format, String expected) {
        String legacy = format.toLegacyText();
        BaseComponent[] components = roundTrip(format);
        assertTrue(events(components).toString(), events(components).isEmpty());
        assertEquals(expected, legacy);
        assertEquals(expected, BaseComponent.toPlainText(components));
        assertEquals(expected, format.toLegacyText());
    }

    private static BaseComponent[] roundTrip(ClickableFormat format) {
        return ComponentSerializer.parse(ComponentSerializer.toString(format.makeTextComponent()));
    }

    private static void assertEvents(BaseComponent[] components, ClickEvent.Action action, String command) {
        List<ClickEvent> events = events(components);
        assertFalse(events.isEmpty());
        for ( ClickEvent event : events ) {
            assertEquals(action, event.getAction());
            assertEquals(command, event.getValue());
        }
    }

    private static List<ClickEvent> events(BaseComponent[] components) {
        List<ClickEvent> events = new ArrayList<>();
        for ( BaseComponent component : components ) {
            if ( component.getClickEvent() != null ) {
                events.add(component.getClickEvent());
            }
            if ( component.getExtra() != null ) {
                events.addAll(events(component.getExtra().toArray(new BaseComponent[0])));
            }
        }
        return events;
    }
}
