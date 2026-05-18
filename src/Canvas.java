/**
 * Canvas — Tegner et grid med forgrunds- og baggrundsfarver (1 tegn per celle).
 *
 * Erstatter BoardDisplay fra Assignment 4. Den store forskel er at Canvas
 * IKKE har et væg-system — i SheepEscape er vægge bare entities (StoneWall,
 * Fence osv.) som fylder en hel celle, i stedet for at sidde mellem celler.
 *
 * Sådan virker det:
 *   1. Dungeon kalder clear() for at nulstille hele grid
 *   2. Hver Entity kalder set() via renderOn() for at "tegne" sig selv
 *   3. Dungeon kalder show() som printer hele grid med ANSI-farver
 *
 * Lagring:
 *   - content[x][y]  = det tegn der vises (f.eks. '#', '@', '.')
 *   - fgColor[x][y]  = forgrunds/tekstfarve
 *   - bgColor[x][y]  = baggrundsfarve
 */
public class Canvas {

    private int rows;   // Antal rækker (højde)
    private int cols;   // Antal kolonner (bredde)

    // Tre parallelle 2D-arrays — ét for indhold, ét for fg-farve, ét for bg-farve.
    // Alle er indekseret som [x][y] (kolonne først, så række).
    private char[][] content;
    private Color[][] fgColor;
    private Color[][] bgColor;

    /**
     * Constructor — opret et tomt Canvas med givne dimensioner.
     * Bemærk: parameterordenen er (cols, rows) = (bredde, højde),
     * fordi cols svarer til x og rows svarer til y.
     */
    public Canvas(int cols, int rows) {
        this.rows = rows;
        this.cols = cols;
        this.content = new char[cols][rows];
        this.fgColor = new Color[cols][rows];
        this.bgColor = new Color[cols][rows];
        clear();  // Fyld med tomme celler fra start
    }

    /**
     * set() — sæt indhold og farver for én celle.
     * Sikkerhedstjek: ignorerer koordinater der er uden for brættet,
     * så vi ikke får ArrayIndexOutOfBoundsException.
     */
    public void set(int x, int y, char ch, Color fg, Color bg) {
        if (x >= 0 && x < cols && y >= 0 && y < rows) {
            content[x][y] = ch;
            fgColor[x][y] = fg;
            bgColor[x][y] = bg;
        }
    }

    /**
     * clear() — nulstil alle celler til mellemrum med sort baggrund.
     * Kaldes i starten af hver frame (før entities tegner sig selv).
     */
    public void clear() {
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                content[x][y] = ' ';
                fgColor[x][y] = Color.WHITE;
                bgColor[x][y] = Color.BLACK;
            }
        }
    }

    /**
     * show() — print hele grid til konsollen med ANSI fg+bg farver.
     *
     * Vi bruger StringBuilder i stedet for System.out.print() for hvert tegn,
     * fordi det er MEGET hurtigere — ellers flimrer skærmen.
     * Hele brættet bygges op som én lang String og printes i ét kald.
     */
    public void show() {
        StringBuilder sb = new StringBuilder();

        // Gennemløb række for række (y = rækker, x = kolonner)
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                Color fg = fgColor[x][y];
                Color bg = bgColor[x][y];
                // Tilføj ANSI-farvekode + selve tegnet
                sb.append(Color.colorCode(bg, fg));
                sb.append(content[x][y]);
            }
            // Nulstil farver og gå til næste linje
            sb.append(Color.RESET);
            sb.append('\n');
        }
        sb.append(Color.RESET);  // Ekstra reset til sidst for en sikkerheds skyld

        System.out.print(sb.toString());
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
}
