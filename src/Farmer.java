import java.util.List;
import java.util.ArrayList;

/**
 * Farmer — Patruljerende fjende (F).
 *
 * Farmeren følger en fast rute defineret som en liste af positioner.
 * Hver tur flytter den til næste punkt i ruten.
 * Når den når enden af ruten, starter den forfra (cyklisk med modulo).
 *
 * Farmeren er solid — spilleren kan ikke gå igennem den.
 * Hvis farmeren lander på spillerens felt, giver den 30 skade.
 *
 * Ruter defineres i level-filerne med ROUTE-linjer.
 * Eksempel: "ROUTE 0 5,3 6,3 7,3 6,3" = farmer #0 patruljer vandret
 *
 * Symbol: 'F'
 * Farve: rød tekst på mørkegrøn baggrund
 * Solid: true (blokerer spillerens bevægelse)
 * Skade: 30 HP ved kontakt
 */
public class Farmer extends Entity {

    private static final int DAMAGE = 30;
    private List<Position> route;   // Listen af positioner farmeren følger
    private int routeIndex;          // Nuværende index i ruten

    public Farmer(Position position) {
        super(position, 'F', Color.RED, Color.DARK_GREEN, true);
        this.sprite = "👨";
        // Default rute = bare startpositionen (farmeren står stille)
        this.route = new ArrayList<>();
        this.route.add(new Position(position.getX(), position.getY()));
        this.routeIndex = 0;
    }

    /** Sæt hele ruten (kaldes af DungeonLoader.parseRoute()). */
    public void setRoute(List<Position> route) {
        this.route = route;
        this.routeIndex = 0;
    }

    /** Tilføj ét punkt til ruten. */
    public void addRoutePoint(Position pos) {
        route.add(pos);
    }

    /**
     * onTurn() — flyt til næste punkt i ruten.
     *
     * Modulo-operatoren (%) sørger for at ruten er cyklisk:
     *   index 0 → 1 → 2 → 0 → 1 → 2 → ...
     *
     * Tjekker også om farmeren lander på spillerens felt.
     */
    @Override
    public void onTurn(Dungeon dungeon) {
        // Hvis ruten kun har ét punkt, står farmeren stille
        if (route.size() <= 1) return;

        // Gå til næste punkt (cyklisk med modulo)
        routeIndex = (routeIndex + 1) % route.size();
        Position next = route.get(routeIndex);
        this.position = next;

        // Tjek om farmeren rammer spilleren
        Player player = dungeon.getPlayer();
        if (player != null && position.equals(player.getPosition())) {
            player.damage(DAMAGE);
            dungeon.beep();
            dungeon.addMessage("Farmeren fangede dig! -" + DAMAGE + " HP");
        }
    }

    public List<Position> getRoute() {
        return route;
    }
}
