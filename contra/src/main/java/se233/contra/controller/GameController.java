package se233.contra.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se233.contra.model.Boss;       // NEW
import se233.contra.model.Boss1;
import se233.contra.model.Boss2;      // NEW

import se233.contra.model.Explosion;
import se233.contra.model.Player;
import se233.contra.model.Soldier;
import se233.contra.exception.GameException;
import se233.contra.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameController {
    private static final Logger logger = LoggerFactory.getLogger(GameController.class);

    public enum GameState {
        MENU,
        MINION_WAVE,
        BOSS_FIGHT,
        GAME_OVER,
        VICTORY
    }

    private GameState currentState;
    private Player player;
    private List<Soldier> soldiers;
    private Boss boss;
    private int bossStage = 0; // 0:none, 1:Boss1, 2:Boss2
    private List<Explosion> explosions;

    //After Defeated Boss
    private boolean intermissionAfterBoss1 = false;
    // When true, we are in pre-Boss2 minion waves
    private boolean preBoss2Waves = false;


    // Wave management
    private int currentWave;
    private int minionsKilled;
    private double waveTimer;
    private boolean waveComplete;

    // Pause
    private boolean paused;

    private final Random random;

    public GameController() {
        this.currentState = GameState.MENU;
        this.soldiers = new ArrayList<>();
        this.explosions = new ArrayList<>();
        this.random = new Random();
        this.paused = false;

        logger.info("GameController initialized");
    }

    // GameController.java
    public void startGame() {
        try {
            logger.info("Starting new game...");

            // FULL RESET 🔧
            if (boss != null) boss = null;   // <<< important: drop the old boss
            soldiers.clear();
            explosions.clear();

            // Reset stage flags
            bossStage = 1;                 // fresh stage 1
            preBoss2Waves = false;
            intermissionAfterBoss1 = false;

            // New player
            player = new Player(100, Constants.GROUND_Y);

            // Wave bookkeeping
            currentWave = 0;
            minionsKilled = 0;
            waveTimer = 0;
            waveComplete = false;

            paused = false;

            // Start at minion waves
            currentState = GameState.MINION_WAVE;
            spawnMinionWave();

            logger.info("Game started successfully");
        } catch (Exception e) {
            throw new GameException("Failed to start game",
                    GameException.ErrorType.INVALID_GAME_STATE, e);
        }
    }


    private void spawnMinionWave() {
        currentWave++;
        logger.info("Spawning minion wave {}/{}", currentWave,
                Constants.MINION_WAVES_BEFORE_BOSS);

        soldiers.clear();

        for (int i = 0; i < Constants.MINIONS_PER_WAVE; i++) {
            double spawnX = Constants.SCREEN_WIDTH + 50 + (i * 100);
            double spawnY = Constants.GROUND_Y;
            soldiers.add(new Soldier(spawnX, spawnY));
        }
    }

    private void spawnBoss() {
        logger.info("Spawning Boss 1!");
        boss = new Boss1(Constants.BOSS1_X, Constants.BOSS1_Y);
        bossStage = 1;
        currentState = GameState.BOSS_FIGHT;
    }

    private void spawnBoss2() {
        logger.info("Spawning Boss 2!");
        // spawn from right side on ground
        double x = 880;
        double y = Constants.GROUND_Y - Constants.BOSS2_FRAME_H;
        boss = new Boss2(x, y);
        bossStage = 2;
        currentState = GameState.BOSS_FIGHT;
    }

    private void resetForBoss2FreshStart() {
        // Clean up previous stage
        explosions.clear();
        soldiers.clear();
        boss = null;

        // Reset player and stats
        player = new Player(100, Constants.GROUND_Y);
        currentWave = 0;
        minionsKilled = 0;
        waveTimer = 0;
        waveComplete = false;
        paused = false;

        // ✅ prepare to use Boss2 background immediately
        bossStage = 2;  // mark that we’re in stage 2
        preBoss2Waves = true;
    }



    public void update(double deltaTime) {
        if (paused || currentState == GameState.MENU) {
            return;
        }

        try {
            handleInput();

            switch (currentState) {
                case MINION_WAVE -> updateMinionWave(deltaTime);
                case BOSS_FIGHT -> updateBossFight(deltaTime);
                case GAME_OVER -> updateGameOver(deltaTime);
                case VICTORY -> updateVictory(deltaTime);
            }

            // Update explosions
            explosions.removeIf(e -> !e.isActive());
            for (Explosion explosion : explosions) {
                explosion.update(deltaTime);
            }

            InputHandler.getInstance().update();

        } catch (Exception e) {
            logger.error("Error in game update", e);
            throw new GameException("Game update failed",
                    GameException.ErrorType.INVALID_GAME_STATE, e);
        }
    }

    private void handleInput() {
        InputHandler input = InputHandler.getInstance();

        // Pause
        if (input.isKeyJustPressed(Constants.KEY_PAUSE)) {
            paused = !paused;
            logger.info("Game {}", paused ? "paused" : "resumed");
        }

        // Restart
        if (input.isKeyJustPressed(Constants.KEY_RESTART)) {
            if (currentState == GameState.GAME_OVER ||
                    currentState == GameState.VICTORY) {
                startGame();
            }
        }

        // Continue from the "congratulations" (Victory) screen into Boss2
        if (currentState == GameState.VICTORY && intermissionAfterBoss1) {
            if (input.isKeyJustPressed(javafx.scene.input.KeyCode.ENTER)) {
                logger.info("ENTER pressed — switching to Boss2 stage (with minion waves)");
                intermissionAfterBoss1 = false;

                // Fresh stage with boss2 background
                resetForBoss2FreshStart();
                preBoss2Waves = true;

                // ✅ Immediately switch to Boss2 area visually
                currentState = GameState.MINION_WAVE;

                // Start first minion wave for Boss2 stage
                spawnMinionWave();
            }
        }



    }

    private void updateMinionWave(double deltaTime) {
        // Update player
        player.update(deltaTime);

        // Update soldiers
        soldiers.removeIf(s -> !s.isActive());
        for (Soldier soldier : soldiers) {
            soldier.update(deltaTime);

            if (soldier.isDead() && !soldier.isActive()) {
                minionsKilled++;
                addExplosion(soldier.getPosition().getX(), soldier.getPosition().getY());
            }
        }

        // Check collisions
        CollisionDetector.checkPlayerBulletsVsSoldiers(player.getBullets(), soldiers, player);
        CollisionDetector.checkSoldierBulletsVsPlayer(soldiers, player);

        // Check wave completion
        if (soldiers.isEmpty()) {
            waveTimer += deltaTime;
            if (waveTimer > 2.0) {
                if (currentWave < Constants.MINION_WAVES_BEFORE_BOSS) {
                    spawnMinionWave();
                    waveTimer = 0;
                } else {
                    // Normal → Boss1, preBoss2 → Boss2
                    if (preBoss2Waves) {
                        spawnBoss2();
                        preBoss2Waves = false;
                    } else {
                        spawnBoss();
                    }
                }
            }
        }

        // Check game over
        if (!player.isAlive()) {
            currentState = GameState.GAME_OVER;
            logger.info("Game Over! Final Score: {}", player.getScore());
        }
    }

    private void updateBossFight(double deltaTime) {
        // Update player
        player.update(deltaTime);

        // Update boss
        if (boss != null && boss.isActive()) {
            boss.update(deltaTime);

            // Check collisions
            // collisions depend on which boss we are fighting
            if (boss instanceof Boss1 b1) {
                CollisionDetector.checkPlayerBulletsVsBoss1(player.getBullets(), b1, player);
            } else if (boss instanceof Boss2 b2) {
                CollisionDetector.checkPlayerBulletsVsBoss2(player.getBullets(), b2, player);
            }
            // boss bullets → player (generic)
            CollisionDetector.checkBossBulletsVsPlayer(boss, player);

            // progression
            if (boss.isBossDefeated()) {
                if (bossStage == 1) {
                    // ✅ show your existing Victory screen as a "congratulations / continue" screen
                    addExplosion(boss.getPosition().getX() + 100, boss.getPosition().getY() + 100);
                    currentState = GameState.VICTORY;
                    intermissionAfterBoss1 = true;   // mark as between Boss1 and Boss2
                    logger.info("Boss 1 defeated — waiting ENTER to start Boss 2");
                    return;
                } else {
                    // ✅ final victory (Boss2 beaten)
                    currentState = GameState.VICTORY;
                    intermissionAfterBoss1 = false;
                    addExplosion(boss.getPosition().getX() + 100, boss.getPosition().getY() + 100);
                    logger.info("Victory! Final Score: {}", player.getScore());
                    return;
                }
            }



        }

        // Check game over
        if (!player.isAlive()) {
            currentState = GameState.GAME_OVER;
            logger.info("Game Over! Final Score: {}", player.getScore());
        }
    }

    private void updateGameOver(double deltaTime) {
        // Show game over screen
    }

    private void updateVictory(double deltaTime) {
        // Show victory screen
    }

    private void addExplosion(double x, double y) {
        explosions.add(new Explosion(x, y));
    }

    public void togglePause() {
        paused = !paused;
        logger.info("Game {}", paused ? "paused" : "resumed");
    }


    // Getters
    public GameState getCurrentState() { return currentState; }
    public Player getPlayer() { return player; }
    public List<Soldier> getSoldiers() { return soldiers; }
    public Boss getBoss() { return boss; }
    public int getBossStage() { return bossStage; }
    public List<Explosion> getExplosions() { return explosions; }
    public boolean isPaused() { return paused; }
    public int getCurrentWave() { return currentWave; }
    public boolean isIntermissionAfterBoss1() {
        return intermissionAfterBoss1;
    }

}