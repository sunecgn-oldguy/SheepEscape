import java.util.ArrayList;
import java.util.List;

/**
 * Dungeon — Spillebrættet / "kernen" i spillet.
 *
 * Erstatter Board fra Assignment 4. Dungeon holder styr på:
 *   - Alle entities (vægge, fjender, items, gulv, spilleren)
 *   - Canvas (til at tegne brættet)
 *   - Spilstatus (vundet, tabt, level gennemført)
 *   - Beskedlog (beskeder der vises til spilleren)
 *
 * De vigtigste metoder:
 *   move()   — flyt spilleren og håndter interaktioner
 *   show()   — tegn brættet på Canvas
 *   tick()   — kald onTurn() på alle entities (enemy AI)
 *
 * Dungeon ved IKKE noget om input eller UI — det er Game's ansvar.
 * Denne opdeling (Dungeon = logik, Game = UI) er god OOP-praksis.
 */
public class Dungeon {

    private int rows;                // Antal rækker
    private int cols;                // Antal kolonner
    private List<Entity> entities;   // ALLE entities på brættet
    private Player player;           // Reference til spilleren (for nem adgang)
    private Canvas canvas;           // Tegne-objekt
    private int levelNumber;         // Hvilket level er dette?
    private boolean levelComplete;   // Har spilleren nået udgangen?
    private boolean gameWon;         // Har spilleren nået Freedom?
    private List<String> messages;   // Beskedlog til UI

    public Dungeon(int cols, int rows, int levelNumber) {
        this.rows = rows;
        this.cols = cols;
        this.levelNumber = levelNumber;
        this.entities = new ArrayList<>();
        this.canvas = new Canvas(cols, rows);
        this.levelComplete = false;
        this.gameWon = false;
        this.messages = new ArrayList<>();
    }

    // ====================================================================
    //  BEVÆGELSE
    // ====================================================================

    /**
     * move() — flyt spilleren ét felt i en retning.
     *
     * Trin:
     *   1. Beregn ny position
     *   2. Tjek grænser (er vi inden for brættet?)
     *   3. Tjek om der er en solid entity i vejen (mur, hegn, fjende)
     *   4. Flyt spilleren
     *   5. Kald onStep() på alle entities på den nye position
     *      (dette trigger items, fælder, gates osv.)
     */
    public void move(Direction dir) {
        if (player == null || player.isDead()) return;

        Position newPos = player.getPosition().moved(dir);

        // Tjek om vi er inden for brættets grænser
        if (newPos.getX() < 0 || newPos.getX() >= cols
            || newPos.getY() < 0 || newPos.getY() >= rows) {
            return;  // Uden for brættet — bevægelse ignoreret
        }

        // Tjek om der er noget solidt i vejen (mur, hegn, fjende)
        if (isSolidAt(newPos)) {
            return;  // Blokeret — spilleren kan ikke flytte dertil
        }

        // Alt er frit — flyt spilleren
        player.moveTo(newPos);

        // Trigger onStep() for alle entities på den nye position.
        // Vi laver en kopi af listen, da onStep() kan ændre entities-listen
        // (f.eks. når et item fjernes fra brættet).
        List<Entity> atPosition = getEntitiesAt(newPos);
        for (Entity e : atPosition) {
            if (e != player) {
                e.onStep(player, this);
            }
        }
    }

    // ====================================================================
    //  RENDERING
    // ====================================================================

    /**
     * show() — tegn hele brættet.
     * Først tegnes alle entities undtagen spilleren (gulv, vægge, items osv.),
     * derefter tegnes spilleren OVENPÅ — så @ altid er synlig.
     */
    public void show() {
        canvas.clear();

        // Tegn alt undtagen spilleren først (baggrundslag)
        for (Entity e : entities) {
            if (!(e instanceof Player)) {
                e.renderOn(canvas);
            }
        }
        // Tegn spilleren ovenpå (forgrundslag)
        if (player != null) {
            player.renderOn(canvas);
        }

        canvas.show();
    }

    // ====================================================================
    //  TUR-LOGIK
    // ====================================================================

    /**
     * tick() — kald onTurn() på alle entities (undtagen spilleren).
     * Dette er "world update" — fjender bevæger sig, bomber tæller ned osv.
     *
     * VIGTIGT: Vi laver en snapshot (kopi) af entity-listen INDEN loopet,
     * fordi onTurn() kan fjerne entities (f.eks. en bombe der eksploderer).
     * Uden snapshot ville vi få ConcurrentModificationException.
     */
    public void tick() {
        List<Entity> snapshot = new ArrayList<>(entities);
        for (Entity e : snapshot) {
            // Kald kun onTurn() hvis entity'en stadig eksisterer i dungeon
            if (e != player && entities.contains(e)) {
                e.onTurn(this);
            }
        }
    }

    // ====================================================================
    //  ENTITY-HÅNDTERING
    // ====================================================================

    /**
     * addEntity() — tilføj en entity til dungeon.
     * Hvis det er en Player, gemmes en reference til den for hurtig adgang.
     */
    public void addEntity(Entity entity) {
        entities.add(entity);
        if (entity instanceof Player) {
            this.player = (Player) entity;
        }
    }

    /** removeEntity() — fjern en entity fra dungeon. */
    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    /**
     * getEntityAt() — find første "interessante" entity på en position.
     * Ignorerer Floor, da det altid ligger under alt andet.
     */
    public Entity getEntityAt(Position pos) {
        for (Entity e : entities) {
            if (e.getPosition().equals(pos) && !(e instanceof Floor)) {
                return e;
            }
        }
        return null;
    }

    /** getEntitiesAt() — find ALLE entities på en position (inkl. Floor). */
    public List<Entity> getEntitiesAt(Position pos) {
        List<Entity> result = new ArrayList<>();
        for (Entity e : entities) {
            if (e.getPosition().equals(pos)) {
                result.add(e);
            }
        }
        return result;
    }

    /** getSolidAt() — find første solide entity på en position (eller null). */
    public Entity getSolidAt(Position pos) {
        for (Entity e : entities) {
            if (e.getPosition().equals(pos) && e.isSolid()) {
                return e;
            }
        }
        return null;
    }

    /** isSolidAt() — er der noget solidt på denne position? */
    public boolean isSolidAt(Position pos) {
        return getSolidAt(pos) != null;
    }

    // ====================================================================
    //  BESKEDLOG
    // ====================================================================

    /** Tilføj en besked til loggen (maks 5 vises ad gangen). */
    public void addMessage(String msg) {
        messages.add(msg);
        while (messages.size() > 5) {
            messages.remove(0);  // Fjern ældste besked
        }
    }

    /**
     * beep() — afspil et terminal-bip (ASCII BEL, tegn 7).
     * Virker i de fleste terminaler (Windows Terminal, cmd, osv.).
     * Bruges til at signalere skade, eksplosioner og vigtige hændelser.
     */
    public void beep() {
        System.out.print('\007');
    }

    public List<String> getMessages() { return messages; }
    public void clearMessages() { messages.clear(); }

    // ====================================================================
    //  SPILSTATUS
    // ====================================================================

    public boolean isGameWon() { return gameWon; }
    public void setGameWon(boolean won) { this.gameWon = won; }
    public boolean isLevelComplete() { return levelComplete; }
    public void setLevelComplete(boolean complete) { this.levelComplete = complete; }

    /** Spillet er tabt hvis spilleren er død. */
    public boolean isGameLost() { return player != null && player.isDead(); }

    // ====================================================================
    //  GETTERS
    // ====================================================================

    public Player getPlayer() { return player; }
    public List<Entity> getEntities() { return entities; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public int getLevelNumber() { return levelNumber; }
}
