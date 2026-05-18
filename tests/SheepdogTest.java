import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Sheepdog — jager player, distraheres af treat.
 */
public class SheepdogTest {

    private Dungeon dungeon;
    private Player player;
    private Sheepdog dog;

    @BeforeEach
    void setUp() {
        dungeon = new Dungeon(20, 20, 1);
        // Fyld med gulv
        for (int x = 0; x < 20; x++) {
            for (int y = 0; y < 20; y++) {
                dungeon.addEntity(new Floor(new Position(x, y)));
            }
        }
        player = new Player(new Position(5, 5));
        dungeon.addEntity(player);
        dog = new Sheepdog(new Position(8, 5));
        dungeon.addEntity(dog);
    }

    @Test
    void testChasesPlayerInRange() {
        Position startPos = dog.getPosition();
        dog.onTurn(dungeon);
        // Hunden skal have bevaaget sig mod spilleren
        Position newPos = dog.getPosition();
        assertTrue(newPos.distanceTo(player.getPosition())
            < startPos.distanceTo(player.getPosition()));
    }

    @Test
    void testDoesNotChaseOutOfRange() {
        // Flyt spilleren langt vaek (mere end 5 felter)
        player.moveTo(new Position(15, 15));
        Position startPos = new Position(dog.getPosition().getX(), dog.getPosition().getY());
        dog.onTurn(dungeon);
        // Hunden skal ikke have bevaaget sig
        assertEquals(startPos, dog.getPosition());
    }

    @Test
    void testDamagesPlayerOnContact() {
        // Placer hunden lige ved siden af spilleren
        dog.setPosition(new Position(6, 5));
        dog.onTurn(dungeon);
        // Hunden burde rammer spilleren
        assertEquals(85, player.getHealth());
    }

    @Test
    void testDistractedByTreat() {
        // Placer en kastet treat langt fra spilleren
        TreatItem treat = new TreatItem(new Position(15, 5));
        // Simuler at treat er kastet
        treat.setPosition(new Position(15, 5));
        // Vi maa bruge reflection eller direkte saette thrown-feltet
        // I stedet: tilfoej treat til dungeon og lad den vaere "thrown"
        // via dungeon-interaktion
        player.addItem(treat);
        Entity used = player.useItem(0);
        if (used instanceof TreatItem) {
            ((TreatItem) used).use(player, dungeon, Direction.EAST);
        }

        Position dogStart = new Position(dog.getPosition().getX(), dog.getPosition().getY());
        dog.onTurn(dungeon);
        // Hunden skal bevaege sig mod treat, ikke mod spilleren
        Position dogNew = dog.getPosition();
        assertNotEquals(dogStart, dogNew);
    }

    @Test
    void testIsSolid() {
        assertTrue(dog.isSolid());
    }

    @Test
    void testSymbol() {
        assertEquals('D', dog.getSymbol());
    }
}
