import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * Game — Hovedklassen med main() og spil-loopet.
 *
 * Denne klasse styrer hele spiloplevelsen:
 *   1. Vis velkomstskærm
 *   2. Load level fra fil (via DungeonLoader)
 *   3. Spil-loop: vis bræt → læs input → bevæg → enemy AI → tjek status
 *   4. Håndter level-skift, game over og sejr
 *
 * Game klassen er ansvarlig for ALT der har med UI at gøre:
 *   - Konsol-input (Scanner)
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

    private Dungeon dungeon;       // Det aktuelle level
    private int currentLevel;      // Hvilket level vi er på (1-4)
    private int totalLevels;       // Totalt antal levels
    private boolean playing;       // Er spillet i gang?
    private Scanner scanner;       // Til at læse bruger-input
    private String[] levelFiles;   // Stier til level-filerne

    /** Constructor — sæt default-værdier og level-stier. */
    public Game() {
        this.currentLevel = 1;
        this.totalLevels = 4;
        this.playing = true;
        this.scanner = new Scanner(System.in);
        this.levelFiles = new String[] {
            "levels/level1.txt",
            "levels/level2.txt",
            "levels/level3.txt",
            "levels/level4.txt"
        };
    }

    /**
     * play() — hovedloopet. Kører indtil spilleren vinder, taber eller quitter.
     *
     * Strukturen er en klassisk game loop:
     *   while (playing) {
     *       render();    // Vis brættet
     *       input();     // Læs spillerens handling
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
        System.out.println("  w/a/s/d  - Bevæg dig (nord/vest/syd/øst)");
        System.out.println("  1-9      - Brug item fra inventory");
        System.out.println("  q        - Afslut spillet");
        System.out.println();
        System.out.println("Tryk Enter for at starte...");
        scanner.nextLine();

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

            // --- INPUT ---
            System.out.print("\n> ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.isEmpty()) continue;  // Tom input — gør ingenting

            // Fortolk input: bevægelse, item-brug eller quit
            Direction dir = null;
            switch (input) {
                case "w": dir = Direction.NORTH; break;
                case "s": dir = Direction.SOUTH; break;
                case "a": dir = Direction.WEST; break;
                case "d": dir = Direction.EAST; break;
                case "q":
                    playing = false;
                    System.out.println("Spillet afsluttet.");
                    continue;
                default:
                    // Tjek om det er et item-nummer (1-9)
                    if (input.length() == 1 && input.charAt(0) >= '1' && input.charAt(0) <= '9') {
                        int itemIndex = input.charAt(0) - '1';  // '1' → 0, '2' → 1, osv.
                        useItem(itemIndex);
                        dungeon.tick();  // Fjender reagerer også når man bruger items
                    } else {
                        System.out.println("Ugyldigt input! Brug w/a/s/d, 1-9 eller q.");
                    }
                    continue;  // Spring bevægelse over — vi brugte et item
            }

            // --- UPDATE ---
            if (dir != null) {
                dungeon.move(dir);   // Flyt spilleren
            }
            dungeon.tick();          // Enemy AI og timers

            // --- CHECK ---
            if (dungeon.isGameWon()) {
                // Spilleren nåede Freedom (*) — spillet er vundet!
                clearConsole();
                dungeon.show();
                System.out.println();
                System.out.print('\007');
                System.out.println("*** TILLYKKE! Du slap fri! ***");
                System.out.println("Fåret fandt friheden!");
                playing = false;

            } else if (dungeon.isLevelComplete()) {
                // Spilleren nåede Gate (>) — gå til næste level
                if (currentLevel < totalLevels) {
                    currentLevel++;
                    System.out.println("Level gennemført! Næste level...");
                    pause();
                    loadLevel(currentLevel);
                } else {
                    // Sidste level gennemført
                    clearConsole();
                    dungeon.show();
                    System.out.println();
                    System.out.print('\007');
                    System.out.println("*** TILLYKKE! Du klarede alle levels! ***");
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
                // Loop indtil spilleren svarer j eller n
                while (true) {
                    System.out.println("Prøv igen? (j/n)");
                    String retry = scanner.nextLine().trim().toLowerCase();
                    if (retry.equals("j")) {
                        loadLevel(currentLevel);  // Genstart level
                        break;
                    } else if (retry.equals("n")) {
                        playing = false;
                        break;
                    }
                    System.out.println("Skriv 'j' for ja eller 'n' for nej.");
                }
            }
        }

        scanner.close();
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
            playing = false;
        }
    }

    /**
     * useItem() — brug et item fra spillerens inventory.
     * Forskellige items har forskellige use()-metoder:
     *   - HayItem: healer spilleren
     *   - BombItem: placerer en bombe
     *   - TreatItem: spørger om retning og kaster godbid
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
            // Treat kræver en retning — spørg spilleren
            System.out.println("Kast retning? (w=nord, s=syd, a=vest, d=øst)");
            String dirInput = scanner.nextLine().trim().toLowerCase();
            Direction throwDir = Direction.NORTH;  // Default hvis ugyldigt input
            switch (dirInput) {
                case "w": throwDir = Direction.NORTH; break;
                case "s": throwDir = Direction.SOUTH; break;
                case "a": throwDir = Direction.WEST; break;
                case "d": throwDir = Direction.EAST; break;
            }
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
        System.out.println("Symboler: 🐑= dig  🧱= mur  🪵= hegn  🔥= el-hegn  💧= vand  🚪= udgang  ⭐= frihed");
        System.out.println("          👨= farmer  🐕= hund  🌾= hø  💣= bombe  🍖= godbid  🚨= alarm");
        System.out.println("Kontroller: w/a/s/d = bevæg | 1-9 = brug item | q = afslut");
    }

    /** Pause — vent på at spilleren trykker Enter. */
    private void pause() {
        System.out.println("Tryk Enter for at fortsætte...");
        scanner.nextLine();
    }

    /**
     * clearConsole() — ryd terminalen.
     * Bruger OS-specifik kommando (cls på Windows, clear på Linux/Mac).
     * Hvis det fejler, printer vi bare 50 tomme linjer som fallback.
     */
    private void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                Runtime.getRuntime().exec("clear");
            }
        } catch (Exception ex) {
            for (int i = 0; i < 50; i++) System.out.println();
        }
    }

    /** Main — entry point for hele programmet. */
    public static void main(String[] args) throws Exception {
        // Sæt konsollen til UTF-8 så emojis vises korrekt i Windows Terminal.
        // Uden dette vil Java bruge systemets default-encoding (ofte CP1252),
        // som ikke kan håndtere emoji-tegn (de vises som '?').
        if (System.getProperty("os.name").contains("Windows")) {
            new ProcessBuilder("cmd", "/c", "chcp 65001").inheritIO().start().waitFor();
        }
        System.setOut(new PrintStream(System.out, true, "UTF-8"));

        Game game = new Game();
        game.play();
    }
}
