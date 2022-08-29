package org.example;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import javax.security.auth.login.LoginException;

public class Main {
    public static JDA jda;
    public static void main(String[] args) throws LoginException, InterruptedException{
        jda = JDABuilder.createLight(tokens.token)
                .addEventListeners(new Hangry())
                .build().awaitReady();
        jda.getGuilds().forEach(guild -> {
            guild.upsertCommand("new-kerfuffle","starts a new kerfuffle game!").queue();
            guild.upsertCommand("leaderboard","shows kerfuffle leaderboard!").queue();
            guild.upsertCommand("execute-query","executes a query on database")
                    .addOption(OptionType.STRING, "sql", "sql statement", true)
                    .addOption(OptionType.STRING, "columns", "columns", true)
                    .queue();
            guild.upsertCommand("execute","executes a SQL cmd on database")
                    .addOption(OptionType.STRING, "sql", "sql statement", true)
                    .queue();
            guild.upsertCommand("cancel","cancels a running kerfuffle game").queue();
        });
    }
}