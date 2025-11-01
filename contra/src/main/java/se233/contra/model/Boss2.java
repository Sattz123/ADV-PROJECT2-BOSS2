package se233.contra.model;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se233.contra.util.Constants;
import se233.contra.util.Vector2D;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Boss 2 – "Java": simple finite-state boss with
 *  - ARC fan shot
 *  - short DASH
 * Shows OOP via inheritance (extends Boss) and composition (owns bullets).
 */
public class Boss2 extends Boss {
    private static final Logger logger = LoggerFactory.getLogger(Boss2.class);
    // --- ANIMATION (sprite sheet 1x3) ---
    private javafx.scene.image.Image spriteSheet;
    private javafx.scene.image.Image[] frames;
    private int frameIndex = 0;
    private double frameTimer = 0.0;
    private double frameDuration = 0.12; // seconds per frame

    // bullet timing
    private double arcBulletInterval = 0.15;  // seconds between bullets
    private double arcBulletTimer = 0.0;      // internal timer for staggered shots
    private int bulletsToFire = 0;            // remaining bullets for the current volley


    private enum Phase { INTRO, IDLE, ARC, DASH, STAGGER, DEAD }

    // vertical movement
    private int vDir = -1; // -1 = up, +1 = down
    private double topBound = 60; // how high it can float (px from top)
    private double bottomBound;   // computed from ground in constructor
    private final double RIGHT_MARGIN = 6; // px from the right edge


    private Phase phase = Phase.INTRO;
    private double phaseTimer = 0;
    private double attackCooldown = 0;
    private final Random rng = new Random();

    private final List<Bullet> bullets = new ArrayList<>();

    public Boss2(double x, double y) {
        // set hitbox to the real frame size
        super(x, y, Constants.BOSS2_FRAME_W, Constants.BOSS2_FRAME_H, Constants.BOSS2_MAX_HEALTH);

        this.bottomBound = Constants.GROUND_Y - Constants.BOSS2_FRAME_H;
        this.facingRight = false;

        // load the sheet directly
        this.spriteSheet = new Image(getClass().getResourceAsStream(Constants.BOSS2_SPRITE));

        // slice into 3 frames and color-key the green background
        final int fw = Constants.BOSS2_FRAME_W;
        final int fh = Constants.BOSS2_FRAME_H;

        frames = new Image[Constants.BOSS2_COLS];
        javafx.scene.image.PixelReader pr = spriteSheet.getPixelReader();

        // exact green in your sheet is (0,61,8) — we’ll allow tiny tolerance
        final int keyR = 0, keyG = 61, keyB = 8, tol = 4;

        for (int i = 0; i < Constants.BOSS2_COLS; i++) {
            javafx.scene.image.WritableImage out = new javafx.scene.image.WritableImage(fw, fh);
            javafx.scene.image.PixelWriter pw = out.getPixelWriter();
            for (int yPix = 0; yPix < fh; yPix++) {
                for (int xPix = 0; xPix < fw; xPix++) {
                    int sx = i * fw + xPix;
                    int sy = yPix;
                    int argb = pr.getArgb(sx, sy);
                    int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    if (Math.abs(r - keyR) <= tol && Math.abs(g - keyG) <= tol && Math.abs(b - keyB) <= tol) {
                        pw.setArgb(xPix, yPix, 0x00000000); // transparent
                    } else {
                        pw.setArgb(xPix, yPix, argb);
                    }
                }
            }
            frames[i] = out;
        }

        this.facingRight = false;
        logger.info("Boss2 spawned at ({}, {})", x, y);
    }


    @Override
    protected void updateBehavior(double dt) {
        phaseTimer += dt;

        if (health <= 0 && phase != Phase.DEAD) {
            phase = Phase.DEAD;
            defeated = true;
            active = false;
            logger.info("Boss2 defeated");
            return;
        }

        // Always face left and stick to the right border
        facingRight = false;
        double targetX = Constants.SCREEN_WIDTH - Constants.BOSS2_FRAME_W - RIGHT_MARGIN;
        position.setX(targetX);

        // Move vertically every frame (except DEAD)
        if (phase != Phase.DEAD) {
            updateVertical(dt);
        }

        // ===== phase logic (keep your current firing code here) =====
        switch (phase) {
            case INTRO -> {
                if (phaseTimer > 0.5) setPhase(Phase.IDLE);
            }
            case IDLE -> {
                attackCooldown -= dt;
                if (attackCooldown <= 0) {
                    setPhase(Phase.ARC);
                }
            }
            case ARC -> {
                if (phaseTimer < 0.05 && bulletsToFire == 0) {
                    bulletsToFire = 5;
                    arcBulletTimer = 0;
                    frameIndex = 0;
                }

                if (bulletsToFire > 0) {
                    arcBulletTimer -= dt;
                    if (arcBulletTimer <= 0) {
                        fireArcFan(1);
                        bulletsToFire--;
                        arcBulletTimer = arcBulletInterval;
                    }
                }

                if (bulletsToFire == 0 && phaseTimer > 0.7) {
                    attackCooldown = 1.2;
                    setPhase(Phase.STAGGER);
                }
            }
            case STAGGER -> {
                if (phaseTimer > 0.35) setPhase(Phase.IDLE);
            }
            case DEAD -> { /* handled above */ }
        }
    }



    private void setPhase(Phase p) {
        phase = p;
        phaseTimer = 0;
        frameTimer = 0;

        if (p != Phase.ARC) {
            frameIndex = 0; // freeze on first frame when not firing
        }
    }


    private void fireArcFan(int count) {
        double totalSpreadDeg = 50;
        double startDeg = 180 - totalSpreadDeg / 2.0;
        double spawnX = position.getX() + Constants.BOSS2_FRAME_W - 10;
        double spawnY = position.getY() + 48;

        for (int i = 0; i < count; i++) {
            double t = rng.nextDouble(); // small random angle variation
            double angleDeg = startDeg + t * totalSpreadDeg;
            double rad = Math.toRadians(angleDeg);
            Vector2D dir = new Vector2D(Math.cos(rad), Math.sin(rad) * 0.65);

            bullets.add(new Bullet(spawnX, spawnY, dir, false));
        }

        // small animation effect
        frameIndex = (frameIndex + 1) % frames.length;
    }



    @Override
    protected void updateComponents(double dt) {
        // integrate X (Y stays locked)
        position.add(new Vector2D(velocity.getX() * dt, 0));
        bounds.setX(position.getX());
        bounds.setY(position.getY());

        // update bullets
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            b.update(dt);
            if (!b.isActive()) it.remove();
        }
        // advance 3-frame loop
        frameTimer += dt;
        if (frameTimer >= frameDuration) {
            frameTimer -= frameDuration;
            frameIndex = (frameIndex + 1) % frames.length; // 0→1→2→0…
        }

        // animation plays only while firing
        if (phase == Phase.ARC) {
            frameTimer += dt;
            if (frameTimer >= frameDuration) {
                frameTimer -= frameDuration;
                frameIndex = (frameIndex + 1) % frames.length; // 0→1→2→0
            }
        } else {
            frameIndex = 0;
        }


    }

    @Override
    public void render(GraphicsContext gc) {
        if (!active) return;

        Image frame = frames[frameIndex];
        double dw = Constants.BOSS2_FRAME_W;
        double dh = Constants.BOSS2_FRAME_H;
        double dx = position.getX();
        double dy = position.getY();

        // --- always face left ---
        // no horizontal scaling math confusion; just draw directly
        // we keep facingRight=false but ignore it here
        gc.drawImage(frame, dx, dy, dw, dh);

        // hp bar
        double pct = Math.max(0, Math.min(1, getHealthPercentage()));
        double w = 80, h = 6;
        gc.setFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.35));
        gc.fillRect(dx + 8, dy - 14, w, h);
        gc.setFill(javafx.scene.paint.Color.CRIMSON);
        gc.fillRect(dx + 8, dy - 14, w * pct, h);

        // bullets
        for (Bullet b : bullets)
            if (b.isActive()) b.render(gc);
    }



    @Override
    public void attack(double deltaTime) {
        // handled in updateBehavior (pattern-based)
    }

    @Override
    public List<Bullet> getAllBullets() {
        return bullets;
    }

    @Override
    public void takeDamage(int damage) {
        if (!active || defeated) return;
        health -= Math.max(1, damage);
        if (health <= 0) {
            health = 0;
            defeated = true;
            active = false;
        }
    }

    private void updateVertical(double dt) {
        position.setY(position.getY() + vDir * Constants.BOSS2_VERTICAL_SPEED * dt);

        // bounce at top and bottom
        if (position.getY() <= topBound) {
            position.setY(topBound);
            vDir = +1;
        } else if (position.getY() >= bottomBound) {
            position.setY(bottomBound);
            vDir = -1;
        }
    }

}
