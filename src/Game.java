import java.io.PrintStream;
import java.util.List;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Game — Hovedklassen med main() og spil-loopet.
 *
 * Denne klasse styrer hele spiloplevelsen:
 *   1. Vis velkomstskærm
 *   2. Load level fra fil (via DungeonLoader)
 *   3. Spil-loop: vis bræt → læs input → bevæg → enemy AI → tjek status
 *   4. Håndter level-skift, game over og sejr
 *
 * Bruger JLine biblioteket til at læse rå tastetryk (piletaster, mellemrum,
 * tal-taster) uden at spilleren behøver trykke Enter efter hvert træk.
 * Dette giver en langt mere responsiv spiloplevelse.
 *
 * Game klassen er ansvarlig for ALT der har med UI at gøre:
 *   - Konsol-input (JLine Terminal)
 *   - Udskrift af HP-bar, inventory, beskeder
 *   - Clear screen
 *
 * Dungeon klassen er ansvarlig for SPILMEKANIK:
 *   - Bevægelse og kollision
 *   - Entity-interaktioner
 *   - Enemy AI (via tick())
 *
 * Denne opdeling er et eksempel på "Separation of Concerns" (SoC) —
 * UI-logik og spilmekanik er adskilt i forskellige klasser.
 */
public class Game {

    // Specielle tastekoder for piletaster (negative tal så de ikke
    // kolliderer med ASCII-koder som alle er positive).
    private static final int KEY_UP = -1;
    private static final int KEY_DOWN = -2;
    private static final int KEY_RIGHT = -3;
    private static final int KEY_LEFT = -4;

    private Dungeon dungeon;       // Det aktuelle level
    private int currentLevel;      // Hvilket level vi er på (1-4)
    private int totalLevels;       // Totalt antal levels
    private boolean playing;       // Er spillet i gang?
    private Terminal terminal;     // JLine terminal til rå tastetryk
    private String[] levelFiles;   // Stier til level-filerne

    /** Constructor — sæt default-værdier, level-stier og JLine terminal. */
    public Game() throws Exception {
        this.currentLevel = 1;
        this.totalLevels = 4;
        this.playing = true;
        // JLine terminal: giver adgang til rå tastetryk uden Enter.
        // system(true) = brug den rigtige system-terminal (ikke en dummy).
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .build();
        terminal.enterRawMode();  // Slå linje-buffering fra
        this.levelFiles = new String[] {
            "levels/level1.txt",
            "levels/level2.txt",
            "levels/level3.txt",
            "levels/level4.txt"
        };
    }

    /**
     * readKey() — læs ét tastetryk fra terminalen (blokerer indtil tast trykkes).
     *
     * Piletaster sender en escape-sekvens (3 bytes):
     *   VT100 (Windows Terminal): ESC(27) + [(91) + A/B/C/D
     *   Windows Console:          224(0xE0) + H/P/M/K
     *
     * Returnerer: positive int = ASCII-tegn, negative int = piletast-konstant.
     */
    private int readKey() throws Exception {
        int ch = terminal.reader().read();

        if (ch == 27) {
            // ESC modtaget — tjek om det er en piletast-sekvens (VT100).
            // peek(100) venter op til 100ms for at se om der kommer mere data.
            // Hvis timeout (-2): det var bare ESC-tasten alene.
            int next = terminal.reader().peek(100);
            if (next == -2) return 27;  // Bare ESC

            next = terminal.reader().read();
            if (next == '[' || next == 'O') {
                int code = terminal.reader().read();
                switch (code) {
                    case 'A': return KEY_UP;
                    case 'B': return KEY_DOWN;
                    case 'C': return KEY_RIGHT;
                    case 'D': return KEY_LEFT;
                }
            }
            return 27;  // Ukendt escape-sekvens
        } else if (ch == 224 || ch == 0) {
            // Windows Console piletaster (ældre cmd.exe format).
            int code = terminal.reader().read();
            switch (code) {
                case 72: return KEY_UP;     // H = op
                case 80: return KEY_DOWN;   // P = ned
                case 77: return KEY_RIGHT;  // M = højre
                case 75: return KEY_LEFT;   // K = venstre
            }
        }

        return ch;  // Almindeligt tegn (bogstav, tal, mellemrum osv.)
    }

    /**
     * keyToDirection() — konverter et tastetryk til en bevægeretning.
     * Understøtter både piletaster og w/a/s/d.
     * Returnerer null hvis tasten ikke er en retnings-tast.
     */
    private Direction keyToDirection(int key) {
        switch (key) {
            case KEY_UP:    case 'w': case 'W': return Direction.NORTH;
            case KEY_DOWN:  case 's': case 'S': return Direction.SOUTH;
            case KEY_RIGHT: case 'd': case 'D': return Direction.EAST;
            case KEY_LEFT:  case 'a': case 'A': return Direction.WEST;
            default: return null;
        }
    }

    /** Vent på et vilkårligt tastetryk. */
    private void waitForKey() {
        try { readKey(); } catch (Exception e) {}
    }

    /**
     * play() — hovedloopet. Kører indtil spilleren vinder, taber eller quitter.
     *
     * Strukturen er en klassisk game loop:
     *   while (playing) {
     *       render();    // Vis brættet
     *       input();     // Læs spillerens handling (ét tastetryk)
     *       update();    // Opdater verden (bevægelse, AI)
     *       check();     // Tjek om spillet er slut
     *   }
     */
    public void play() {
        // Velkomstskærm med kontroller
        System.out.println("=== SHEEP ESCAPE ===");
        System.out.println("Et får der flygter fra folden!");
        System.out.println();
        System.out.println("Kontroller:");
        System.out.println("  Piletaster / w/a/s/d  - Bevæg dig");
        System.out.println("  1-9                   - Brug item fra inventory");
        System.out.println("  q                     - Afslut spillet");
        System.out.println();
        System.out.println("Tryk en tast for at starte...");
        System.out.flush();
        waitForKey();

        // Load det første level
        loadLevel(currentLevel);

        // ===== GAME LOOP =====
        while (playing) {
            // --- RENDER ---
            clearConsole();
            dungeon.show();          // Tegn brættet
            printPlayerStats();      // Vis HP-bar
            printMessages();         // Vis beskedlog
            printInventory();        // Vis inventory
            printControls();         // Vis kontroller
            System.out.flush();

            // --- INPUT ---
            // Læs ét tastetryk (blokerer her indtil spilleren trykker noget)
            int key;
            try {
                key = readKey();
            } catch (Exception e) {
                continue;
            }

            // Fortolk input: bevægelse, item-brug eller quit
            Direction dir = keyToDirection(key);

            if (dir != null) {
                // --- UPDATE ---
                dungeon.move(dir);   // Flyt spilleren
                dungeon.tick();      // Enemy AI og timers
            } else if (key == 'q' || key == 'Q') {
                playing = false;
                System.out.println("Spillet afsluttet.");
                System.out.flush();
                continue;
            } else if (key >= '1' && key <= '9') {
                // Item-brug
                int itemIndex = key - '1';  // '1' → 0, '2' → 1, osv.
                useItem(itemIndex);
                dungeon.tick();  // Fjender reagerer også når man bruger items
            } else {
                continue;  // Ukendt tast — ignorer
            }

            // --- CHECK ---
            if (dungeon.isGameWon()) {
                // Spilleren nåede Freedom (*) — spillet er vundet!
                clearConsole();
                dungeon.show();
                System.out.println();
                System.out.print('\007');
                System.out.println("*** TILLYKKE! Du slap fri! ***");
                System.out.println("Fåret fandt friheden!");
                System.out.flush();
                playing = false;

            } else if (dungeon.isLevelComplete()) {
                // Spilleren nåede Gate (>) — gå til næste level
                if (currentLevel < totalLevels) {
                    currentLevel++;
                    System.out.println("Level gennemført! Næste level...");
                    System.out.flush();
                    waitForKey();
                    loadLevel(currentLevel);
                } else {
                    // Sidste level gennemført
                    clearConsole();
                    dungeon.show();
                    System.out.println();
                    System.out.print('\007');
                    System.out.println("*** TILLYKKE! Du klarede alle levels! ***");
                    System.out.flush();
                    playing = false;
                }

            } else if (dungeon.isGameLost()) {
                // Spilleren er død — tilbyd retry
                clearConsole();
                dungeon.show();
                System.out.println();
                System.out.print('\007');
                System.out.println("*** GAME OVER ***");
                System.out.println("Fåret blev fanget...");
                System.out.println();
                System.out.println("Prøv igen? (j/n)");
                System.out.flush();
                // Loop indtil spilleren trykker j eller n
                while (true) {
                    try {
                        int retry = readKey();
                        if (retry == 'j' || retry == 'J') {
                            loadLevel(currentLevel);  // Genstart level
                            break;
                        } else if (retry == 'n' || retry == 'N') {
                            playing = false;
                            break;
                        }
                    } catch (Exception e) {}
                }
            }
        }

        // Luk JLine terminal og gendan normal terminal-tilstand.
        // Uden dette ville terminalen forblive i raw mode efter spillet.
        try { terminal.close(); } catch (Exception e) {}
    }

    /**
     * loadLevel() — load et level fra en .txt fil via DungeonLoader.
     * Hvis filen ikke kan læses, afsluttes spillet med en fejlbesked.
     */
    private void loadLevel(int level) {
        try {
            String filename = levelFiles[level - 1];  // level 1 → index 0
            dungeon = DungeonLoader.load(filename, level);
            dungeon.addMessage("Level " + level + " - Start!");
        } catch (Exception e) {
            System.out.println("Kunne ikke loade level " + level + ": " + e.getMessage());
            System.out.flush();
            playing = false;
        }
    }

    /**
     * useItem() — brug et item fra spillerens inventory.
     * Forskellige items har forskellige use()-metoder:
     *   - HayItem: healer spilleren
     *   - BombItem: placerer en bombe
     *   - TreatItem: spørger om retning (via piletast) og kaster godbid
     *
     * Vi bruger instanceof til at finde den rigtige type,
     * og caster derefter for at kalde den specifikke use()-metode.
     */
    private void useItem(int index) {
        Player player = dungeon.getPlayer();
        if (player == null) return;

        List<Entity> inventory = player.getInventory();
        if (index < 0 || index >= inventory.size()) {
            dungeon.addMessage("Intet item på plads " + (index + 1));
            return;
        }

        // Fjern item'et fra inventory og brug det
        Entity item = player.useItem(index);
        if (item instanceof HayItem) {
            ((HayItem) item).use(player, dungeon);
        } else if (item instanceof BombItem) {
            ((BombItem) item).use(player, dungeon);
        } else if (item instanceof TreatItem) {
            // Treat kræver en retning — læs en piletast fra spilleren
            System.out.println("Kast retning? (piletast eller w/a/s/d)");
            System.out.flush();
            Direction throwDir = Direction.NORTH;  // Default
            try {
                int dirKey = readKey();
                Direction chosen = keyToDirection(dirKey);
                if (chosen != null) throwDir = chosen;
            } catch (Exception e) {}
            ((TreatItem) item).use(player, dungeon, throwDir);
        }
    }

    // ====================================================================
    //  UI-METODER (printer til konsollen)
    // ====================================================================

    /** Vis HP-bar med visuelt fill-level. */
    private void printPlayerStats() {
        Player player = dungeon.getPlayer();
        if (player == null) return;

        System.out.println();
        System.out.println("=== Sheep Escape - Level " + dungeon.getLevelNumber() + " ===");

        // Beregn HP-bar: [########............] 80/100 HP
        int hp = player.getHealth();
        int max = player.getMaxHealth();
        int barLen = 20;
        int filled = (int) ((double) hp / max * barLen);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLen; i++) {
            bar.append(i < filled ? '#' : '.');
        }
        bar.append("] " + hp + "/" + max + " HP");
        System.out.println("HP: " + bar);
    }

    /** Vis inventory med item-numre. */
    private void printInventory() {
        Player player = dungeon.getPlayer();
        if (player == null) return;

        List<Entity> inv = player.getInventory();
        if (inv.isEmpty()) {
            System.out.println("Inventory: (tom)");
        } else {
            System.out.print("Inventory: ");
            for (int i = 0; i < inv.size(); i++) {
                System.out.print("[" + (i + 1) + "] " + inv.get(i));
                if (i < inv.size() - 1) System.out.print("  ");
            }
            System.out.println();
        }
    }

    /** Vis beskedlog (de seneste 5 beskeder). */
    private void printMessages() {
        List<String> msgs = dungeon.getMessages();
        if (!msgs.isEmpty()) {
            for (String msg : msgs) {
                System.out.println("  >> " + msg);
            }
        }
    }

    /** Vis symbolforklaring og kontroller under brættet. */
    private void printControls() {
        System.out.println();
        System.out.println("Symboler: 🐑= dig  🧱= mur  🚧= hegn  🔥= el-hegn  💧= vand  🏁= udgang  ⭐= frihed");
        System.out.println("          👨= farmer  🐕= hund  💛= hø  💣= bombe  🍖= godbid  🚨= alarm");
        System.out.println("Kontroller: piletaster/wasd = bevæg | 1-9 = brug item | q = afslut");
    }

    /**
     * clearConsole() — ryd terminalen med ANSI escape-koder.
     * \033[H  = flyt cursor til øverste venstre hjørne
     * \033[2J = ryd hele skærmen
     */
    private void clearConsole() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    /** Main — entry point for hele programmet. */
    public static void main(String[] args) throws Exception {
        // Sæt konsollen til UTF-8 så emojis vises korrekt i Windows Terminal.
        if (System.getProperty("os.name").contains("Windows")) {
            new ProcessBuilder("cmd", "/c", "chcp 65001")
                .inheritIO().start().waitFor();
        }
        System.setOut(new PrintStream(System.out, true, "UTF-8"));

        Game game = new Game();
        game.play();
    }
}
