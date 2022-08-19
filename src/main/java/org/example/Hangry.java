package org.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.AttachmentOption;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.example.database.connection;

public class Hangry extends ListenerAdapter {
    public static boolean isRunning = false;
    public static String channelId = "";
    public static Message howMany = null;
    public static long started = 0L;
    public static Map<String, Integer> tracker = new HashMap<>();
    public static boolean gameStarted = false;
    public static String serverId = null;
    static String path = "";
    static String title;


    public void onSlashCommandInteraction(SlashCommandInteractionEvent e){
        if ("new-kerfuffle".equalsIgnoreCase(e.getName())) {
            if(isRunning){
                e.replyEmbeds(new EmbedBuilder()
                        .setTitle("Oops!")
                        .setDescription("**A game of kerfuffle is already running on** <#" + channelId + ">")
                        .build()).setEphemeral(true).queue();
                return;
            }
            e.deferReply().queue();
            try {
                sendStarter(e);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }

        }else if(e.getName().equalsIgnoreCase("leaderboard")){
            e.deferReply().queue();
            e.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Kerfuffle win leaderboard")
                    .appendDescription(leaderbaord())
                    .build()).queue();
        }else if(e.getName().equalsIgnoreCase("execute")){
            e.deferReply().queue();
           if(!e.getMember().hasPermission(Permission.MODERATE_MEMBERS)){
               e.getHook().sendMessageEmbeds(new EmbedBuilder()
                       .setTitle("You don't have permission to do that!")
                       .build()).queue();
               return;
           }
            try {
                String sql = e.getOptionsByName("sql").get(0).getAsString();
                connection.createStatement().execute(sql);
                e.getHook().sendMessageEmbeds(new EmbedBuilder()
                                .setTitle("Successfully executed command!")
                                .setDescription("command: \n" +
                                        "```" + sql + "```")
                        .build()).queue();
            } catch (Exception ex) {
                e.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("An error occurred while executing this!")
                        .setDescription("error stack: \n" +
                                "```" +ex.getStackTrace()[0] + "```")
                        .build()).queue();
            }
        }else if(e.getName().equalsIgnoreCase("execute-query")){
            e.deferReply().queue();
            if(!e.getMember().hasPermission(Permission.MODERATE_MEMBERS)){
                e.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("You don't have permission to do that!")
                        .build()).queue();
                return;
            }
            String sql = e.getOptionsByName("sql").get(0).getAsString();
            String[] columns = e.getOptionsByName("columns").get(0).getAsString().split(" ");
            ResultSet result;
            try {
                result = connection.createStatement().executeQuery(sql);
                StringBuilder builder = new StringBuilder();
                Arrays.stream(columns).forEach(column ->{
                    builder.append(column + "  ");
                });
                builder.append("\n");
                while (result.next()){
                    Arrays.stream(columns).forEach(column ->{
                        try {
                            builder.append(result.getObject(column) + "    ");
                        } catch (SQLException ex) {
                            e.getHook().sendMessageEmbeds(new EmbedBuilder()
                                    .setTitle("An error occurred while executing this!")
                                    .setDescription("error stack: \n" +
                                            "```" +ex.getStackTrace()[0] + "```")
                                    .build()).queue();
                            return;
                        }
                    });
                    builder.append(" \n");
                }
                e.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("Successfully executed Query!")
                        .setDescription("result: \n" +
                                "```" + builder + "```")
                        .build()).queue();
            } catch (SQLException ex) {
                e.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("An error occurred while executing this!")
                        .setDescription("error stack: \n" +
                                "```" +ex.getStackTrace()[0] + "```")
                        .build()).queue();

            }
        }else if(e.getName().equalsIgnoreCase("cancel")){
            e.deferReply().queue();
            if(!e.getMember().hasPermission(Permission.MODERATE_MEMBERS)){
                e.getHook().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("You don't have permission to do that!")
                        .build()).queue();
                return;
            }
            serverId = "";
            isRunning = false;
            channelId = "";
            started = 0L;
            gameStarted = false;
            tracker = new HashMap<>();
            howMany = null;
            e.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("Successfully cancelled the game!")
                    .build()).queue();
        }
    }

    public void onButtonInteraction(ButtonInteractionEvent e){
        String id = e.getButton().getId();
        switch (id){
            case "start":
                if(!e.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                   e.replyEmbeds(new EmbedBuilder()
                           .setTitle("⛔ You don't have permission to do that!")
                           .build()).setEphemeral(true).queue();
                   return;
               }
                if(!(tracker.size() >= 2)){
                    e.replyEmbeds(new EmbedBuilder()
                            .setTitle("⛔ Not enough players to start")
                            .build()).setEphemeral(true).queue();
                    return;
                }
                e.replyEmbeds(new EmbedBuilder()
                        .setTitle("⚔️Game started!")
                        .build()).setEphemeral(true).queue();
                sendRemaining();
                gameStarted = true;
                Runnable runnable = () -> {
                    try {
                        start();
                    } catch (SQLException | InterruptedException | IOException ex) {
                        throw new RuntimeException(ex);
                    }
                };
                Thread thread = new Thread(runnable);
                thread.start();
                try {
                    connection.createStatement().execute("CREATE TABLE IF NOT EXISTS servers(_id TEXT PRIMARY KEY, games INTEGER)");
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                break;
            case "join":
                if(tracker.containsKey(e.getMember().getId())){
                    e.replyEmbeds(new EmbedBuilder()
                                    .setTitle("You have already joined!")
                            .build()).setEphemeral(true).queue();
                    return;
                }else if(gameStarted){
                    e.replyEmbeds(new EmbedBuilder()
                            .setTitle("⚔️ Game already started, please join on next game!!")
                            .build()).setEphemeral(true).queue();
                    return;
                }else if(!isRunning){
                    e.replyEmbeds(new EmbedBuilder()
                            .setTitle("⚔️ No games running at the moment!")
                            .build()).setEphemeral(true).queue();
                    return;
                }
                tracker.put(e.getMember().getId(), 0);

                e.replyEmbeds(new EmbedBuilder()
                        .setTitle("\uD83D\uDD25 You are in!")
                        .build()).setEphemeral(true).queue();
                howMany.editMessageEmbeds(new EmbedBuilder()
                        .setTitle("⚔️ " + tracker.size() + " Players Joined")
                        .build()).queue();
        }
    }
    static void sendStarter(SlashCommandInteractionEvent e) throws SQLException {
        serverId = e.getGuild().getId();
        isRunning = true;
        channelId = e.getChannel().getId();

        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS servers(_id TEXT PRIMARY KEY, games INTEGER)");
        int amount;
        try{
             amount = connection.createStatement().executeQuery(String.format("SELECT * FROM servers WHERE _id == '%s'", e.getGuild().getId())).getInt("games");
        }catch (SQLException exception){
            exception.printStackTrace();
            connection.createStatement().execute(String.format("INSERT INTO servers VALUES ('%s', %s)", e.getGuild().getId(), 1));
             amount = connection.createStatement().executeQuery(String.format("SELECT * FROM servers WHERE _id == '%s'", e.getGuild().getId())).getInt("games");
        }

        Button join = Button.success("join", "\uD83D\uDD25 Join");
        Button start = Button.primary("start", "⚔️ start");

         title = e.getGuild().getName() + "'s " + numString(amount) + " Kerfuffle Game";
        MessageBuilder builder = new MessageBuilder(new EmbedBuilder()
                .setTitle(title)
                .addField("Gathering Players", "" +
                        " \n" +
                        ":fire: **to join the fight** \n" +
                        ":crossed_swords: **to start the game**", false)
        );
        builder.setActionRows(ActionRow.of(join), ActionRow.of(start));
        e.getHook().sendMessage( builder.build()).queue( message ->
                e.getChannel().sendMessageEmbeds(new EmbedBuilder()
                        .setTitle("⚔️ " + tracker.size() + " Players Joined")
                        .build()).queue(message1 -> howMany = message1)
        );
    }

    static String leaderbaord(){
        StringBuilder builder = new StringBuilder();
        try {
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS winners(_id TEXT UNIQUE, wins INTEGER)");
            ResultSet result = connection.createStatement().executeQuery("SELECT * FROM winners ORDER BY wins DESC LIMIT 10");
            int i = 0;
            while(result.next()){
                i++;
                builder.append(String.format("`%s.` <@%s> - `%s` wins \n", i, result.getString("_id"), result.getInt("wins")));
            }
        } catch (SQLException ignored) {
            ignored.printStackTrace();
        }

        return builder.toString();
    }

    static String numString(int number){
        char first = (number + "").toCharArray()[(number + "").toCharArray().length -1];
        return switch (first) {
            case '1' -> number + "st";
            case '2' -> number + "nd";
            case '3' -> number + "rd";
            default -> number + "th";
        };
    }

    static void send(String killed, boolean isSingle) throws IOException, SQLException {
        String killMessage;
        String killer;
        if (!isSingle) {
            do {
                Random random = new Random();
                killer = (String) tracker.keySet().toArray()[random.nextInt(tracker.size())];
            } while (killer.equalsIgnoreCase(killed));
            String path = dualImage(killer, killed);
            String killed_name =  Main.jda.getGuildById(serverId).retrieveMemberById(killed).complete().getEffectiveName() ;
            String killer_name =  Main.jda.getGuildById(serverId).retrieveMemberById(killer).complete().getEffectiveName();

            killMessage = "**" + connection.createStatement().executeQuery("SELECT * FROM kill_messages ORDER BY RANDOM() LIMIT 1")
                    .getString("message").replace("x ", killer_name + " ").replace(" x ", " " + killer_name + " ")
                    .replace(" y ", " " + killed_name + " ") + "**";
            File file = new File(path);
            Main.jda.getTextChannelById(channelId).sendMessageEmbeds(new EmbedBuilder()
                            .setDescription(killMessage)
                            .build())
                    .addFile(file).queue(message ->{
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        file.delete();
                            });

            tracker.put(killer, tracker.get(killer) + 1);
        } else {
            String path = oneImage(killed, false);
            String name = "||" + Main.jda.getGuildById(serverId).retrieveMemberById(killed).complete().getEffectiveName() + "||";
            killMessage = "**" + connection.createStatement().executeQuery("SELECT * FROM death_messages ORDER BY RANDOM() LIMIT 1")
                    .getString("message").replace("x ", name + " ").replace(" x ", " " + name + " ") + "**";

            File file = new File(path);
            Main.jda.getTextChannelById(channelId).sendMessageEmbeds(new EmbedBuilder()
                            .setDescription(killMessage)
                            .build())
                    .addFile(file, AttachmentOption.SPOILER).queue((message ->{
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        file.delete();
                    }));

        }
    }
    static void start() throws SQLException, InterruptedException, IOException {
        TextChannel channel = Main.jda.getTextChannelById(channelId);
        channel.sendTyping().queue();
        started = System.currentTimeMillis();
        Random random = new Random();
        random.nextInt();
        boolean kill_vs;
        boolean last_was_kill_vs = false;
        int bound;
        String killed;


        int realSize = tracker.size();
        while (tracker.size() != 1) {
            channel.sendTyping().queue();
            TimeUnit.SECONDS.sleep(9);
            if (tracker.size() % 10 == 0 || tracker.size() == 4 || tracker.size() % Math.round(realSize / 2F) == 0) {
                if(!(realSize == tracker.size())){
                    sendRemaining();
                }
            }
            if (tracker.size() <= 3) {
                killed = (String) tracker.keySet().toArray()[random.nextInt(tracker.size())];
                if (tracker.size() == 3) {
                    send(killed, true);
                } else if (tracker.size() == 2) {
                    send(killed, false);
                } else if (tracker.size() == 1) {
                    break;
                }

            } else {
                bound = last_was_kill_vs ? 10 : 3;
                kill_vs = random.nextInt(bound) != 2;
                killed = (String) tracker.keySet().toArray()[random.nextInt(tracker.size())];
                if (kill_vs) {
                    last_was_kill_vs = true;
                    send(killed, false);
                } else {
                    send(killed, true);
                }
            }
            tracker.remove(killed);
        }
        String winner = (String) tracker.keySet().toArray()[0];
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS winners(_id TEXT UNIQUE, Integer wins)");
        int previousWins;
        try {
            previousWins = connection.createStatement().executeQuery("SELECT wins FROM winners WHERE _id == '" + winner + "'").getInt("wins");
            connection.createStatement().execute(String.format("UPDATE winners SET wins = %s WHERE _id == '%s'", previousWins + 1, winner));
        } catch (SQLException exception) {
            connection.createStatement().execute(String.format("INSERT INTO winners VALUES('%s', 1)", winner));
            previousWins = 1;
        }
        try {
            int previousGames = connection.createStatement().executeQuery("SELECT games FROM servers WHERE _id == '" + serverId + "'").getInt("games");
            connection.createStatement().execute(String.format("UPDATE servers SET games = %s WHERE _id == '%s'", previousGames + 1, serverId));
        } catch (SQLException exception) {
            connection.createStatement().execute(String.format("INSERT INTO servers VALUES('%s', 1)", serverId));
        }
        TimeUnit.SECONDS.sleep(5);
        sendWinner(previousWins);
        channel.sendTyping().queue();
        serverId = "";
        isRunning = false;
        channelId = "";
        started = 0L;
        gameStarted = false;
        tracker = new HashMap<>();
        howMany = null;
    }
    static void sendRemaining(){
        StringBuilder players = new StringBuilder();
        players.append("```");
        Object[] arr = tracker.keySet().toArray();

        for(int i = 0; i < tracker.size(); i++){
            if(i == tracker.size()-1){
                players.append(Main.jda.getGuildById(serverId).retrieveMemberById((String) arr[i]).complete().getEffectiveName() + "```");
                break;
            }
           players.append(Main.jda.getGuildById(serverId).retrieveMemberById((String) arr[i]).complete().getEffectiveName()).append("\n");
        }
       Main.jda.getTextChannelById(channelId).sendMessageEmbeds(new EmbedBuilder()
                .setTitle(tracker.size() + " Players remaining")
                .setDescription(players.toString())
                .build()).queue();
   }

   static void sendWinner(int wins) throws IOException {
        String winner = (String) tracker.keySet().toArray()[0];
           String path = oneImage(winner, true);
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Winner!")
                .setDescription(String.format(":tada: <@%s> won %s \n" +
                        ":skull: **Total kills:** `%s` \n" +
                        ":stopwatch: **Time survived:** `%s` seconds\n" +
                        ":trophy: **Total win in server:** `%s`", winner, title, tracker.get(winner), (System.currentTimeMillis() - started) / 1000, wins));
       File file = new File(path);
       Main.jda.getTextChannelById(channelId).sendMessageEmbeds(builder.build()).addFile(file).queue(
                message ->
               file.delete());
   }
    public static String oneImage(String userId, boolean isWinner) throws IOException {
        BufferedImage frame = isWinner ? ImageIO.read(new File(path +"winner.png")) : ImageIO.read(new File(path +"single.png"));
        BufferedImage overlay_frame = ImageIO.read(new File(path + "frame1.png"));
        BufferedImage pfp = ImageIO.read(new URL(Main.jda.retrieveUserById(userId).complete().getEffectiveAvatarUrl()));
        BufferedImage finalImage = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = finalImage.createGraphics();
        g.drawImage(frame, 0 , 0, null);
        g.drawImage(pfp, 135, 114, 229, 216, null);
        g.drawImage(overlay_frame, 0, 0, null);
        g.dispose();

        ImageIO.write(finalImage, "PNG", new File(userId+".png"));
        return userId+".png";
    }

    public static String dualImage(String user1, String user2) throws IOException {
        BufferedImage pfp1 = ImageIO.read(new URL(Main.jda.retrieveUserById(user1).complete().getEffectiveAvatarUrl()));
        BufferedImage pfp2 = ImageIO.read(new URL(Main.jda.retrieveUserById(user2).complete().getEffectiveAvatarUrl()));

        BufferedImage frame = ImageIO.read(new File(path +"dual.png"));
        BufferedImage overlay = ImageIO.read(new File(path + "frame2.png"));
        BufferedImage vsOverlay = ImageIO.read(new File(path + "vs.png"));

        BufferedImage finalImage = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        String generated = user1+"vs"+user2+".png";

        Graphics2D g = finalImage.createGraphics();
        g.drawImage(frame, 0,0, null);
        g.drawImage(pfp1, 33, 124, 199, 193, null);
        g.drawImage(pfp2, 266, 124, 200, 192, null);
        g.drawImage(overlay, 0, 0, null);
        g.drawImage(vsOverlay, 0, 0, null);
        g.dispose();
        ImageIO.write(finalImage, "PNG", new File(generated));

        return generated;
    }
}
