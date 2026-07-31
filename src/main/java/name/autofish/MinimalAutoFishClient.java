package com.yourname.autofish; // <-- Update this line to match your folder package!

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class MinimalAutoFishClient implements ClientModInitializer {

    // --- Configurations ---
    private static boolean enabled = false;
    private static int minReactionDelayMs = 50;
    private static int maxReactionDelayMs = 250;
    private static int minRecastDelayMs = 1200;
    private static int maxRecastDelayMs = 2000;

    // --- State Machine ---
    private enum FishState {
        IDLE,
        WAITING_FOR_BITE,
        REELING_IN,
        WAITING_TO_RECAST
    }

    private static FishState state = FishState.IDLE;
    private static long targetTimeMs = 0;
    private static final Random random = new Random();

    // --- Keybinding (Hardcoded to V) ---
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        // 1. Register the "V" Keybinding
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autofish.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.autofish"
        ));

        // 2. Chat Command Interceptor (?macro)
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("?macro")) {
                handleMacroCommand(message);
                return false; // Intercept & block message from being sent to Hypixel!
            }
            return true; // Allow normal messages
        });

        // 3. Main Tick Event Loop
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Handle Keybind Press
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                state = FishState.IDLE;
                sendLocalMessage(client, "Auto-Fisher is now " + (enabled ? "§aENABLED" : "§cDISABLED"));
            }

            if (!enabled) return;

            // Ensure player is holding a fishing rod
            if (!client.player.getMainHandStack().isOf(Items.FISHING_ROD)) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            FishingBobberEntity bobber = client.player.fishHook;

            switch (state) {
                case IDLE:
                    if (bobber != null) {
                        state = FishState.WAITING_FOR_BITE;
                    }
                    break;

                case WAITING_FOR_BITE:
                    if (bobber == null) {
                        state = FishState.IDLE;
                        break;
                    }

                    // Passive Velocity Detection: Bobber dips underwater when velocity drops below -0.04
                    if (bobber.getVelocity().y < -0.04) {
                        int reactionDelay = getRandomBetween(minReactionDelayMs, maxReactionDelayMs);
                        targetTimeMs = currentTime + reactionDelay;
                        state = FishState.REELING_IN;
                    }
                    break;

                case REELING_IN:
                    if (currentTime >= targetTimeMs) {
                        // Reel in the fish
                        if (client.interactionManager != null) {
                            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                        }

                        // Schedule recast
                        int recastDelay = getRandomBetween(minRecastDelayMs, maxRecastDelayMs);
                        targetTimeMs = currentTime + recastDelay;
                        state = FishState.WAITING_TO_RECAST;
                    }
                    break;

                case WAITING_TO_RECAST:
                    if (currentTime >= targetTimeMs) {
                        // Cast rod again
                        if (client.interactionManager != null) {
                            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                        }
                        state = FishState.WAITING_FOR_BITE;
                    }
                    break;
            }
        });
    }

    // --- Command Handler ---
    private void handleMacroCommand(String commandText) {
        MinecraftClient client = MinecraftClient.getInstance();
        String[] args = commandText.split(" ");

        if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
            sendLocalMessage(client, "§b--- Auto-Fish Commands ---");
            sendLocalMessage(client, "§e?macro delay <min> <max> §7- Reaction randomizer (ms)");
            sendLocalMessage(client, "§e?macro recast <min> <max> §7- Recast randomizer (ms)");
            sendLocalMessage(client, "§e?macro status §7- View current settings");
            return;
        }

        String subCommand = args[1].toLowerCase();

        if (subCommand.equals("delay") && args.length >= 4) {
            try {
                minReactionDelayMs = Integer.parseInt(args[2]);
                maxReactionDelayMs = Integer.parseInt(args[3]);
                sendLocalMessage(client, "§aReaction delay set to: §e" + minReactionDelayMs + "ms - " + maxReactionDelayMs + "ms");
            } catch (NumberFormatException e) {
                sendLocalMessage(client, "§cInvalid numbers. Usage: ?macro delay 50 250");
            }
        } else if (subCommand.equals("recast") && args.length >= 4) {
            try {
                minRecastDelayMs = Integer.parseInt(args[2]);
                maxRecastDelayMs = Integer.parseInt(args[3]);
                sendLocalMessage(client, "§aRecast delay set to: §e" + minRecastDelayMs + "ms - " + maxRecastDelayMs + "ms");
            } catch (NumberFormatException e) {
                sendLocalMessage(client, "§cInvalid numbers. Usage: ?macro recast 1200 2000");
            }
        } else if (subCommand.equals("status")) {
            sendLocalMessage(client, "§b--- Current Settings ---");
            sendLocalMessage(client, "§fStatus: " + (enabled ? "§aENABLED" : "§cDISABLED"));
            sendLocalMessage(client, "§fReaction Delay: §e" + minReactionDelayMs + "ms - " + maxReactionDelayMs + "ms");
            sendLocalMessage(client, "§fRecast Delay: §e" + minRecastDelayMs + "ms - " + maxRecastDelayMs + "ms");
        } else {
            sendLocalMessage(client, "§cUnknown command. Type §e?macro help §cfor options.");
        }
    }

    // --- Helper Methods ---
    private void sendLocalMessage(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§7[§bAutoFish§7] §f" + text), false);
        }
    }

    private int getRandomBetween(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }
}