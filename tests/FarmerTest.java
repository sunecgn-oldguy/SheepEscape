import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for Farmer — foelger rute, giver skade.
 */
public class FarmerTest {

    private Dungeon dungeon;
    private Player player;
    private Farmer farmer;

    @BeforeEach
    void setUp() {
        dungeon = new Dungeon(10, 10, 1);
        player = new Player(new Position(1, 1));
        dungeon.addEntity(player);
        farmer = new Farmer(new Position(5, 5));
        dungeon.addEntity(farmer);
    }

    @Test
    void testStartPosition() {
        assertEquals(new Position(5, 5), farmer.getPosition());
    }

    @Test
    void testFollowsRoute() {
        List<Position> route = Arrays.asList(
            new Position(5, 5),
            new Position(6, 5),
            new Position(7, 5),
            new Position(6, 5)
        );
        farmer.setRoute(route);

        farmer.onTurn(dungeon);
        assertEquals(new Position(6, 5), farmer.getPosition());

        farmer.onTurn(dungeon);
        assertEquals(new Position(7, 5), farmer.getPosition());

        farmer.onTurn(dungeon);
        assertEquals(new Position(6, 5), farmer.getPosition());
    }

    @Test
    void testRouteWrapsAround() {
        List<Position> route = Arrays.asList(
            new Position(5, 5),
            new Position(6, 5)
        );
        farmer.setRoute(route);

        farmer.onTurn(dungeon);
        assertEquals(new Position(6, 5), farmer.getPosition());

        farmer.onTurn(dungeon);
        assertEquals(new Position(5, 5), farmer.getPosition());

        farmer.onTurn(dungeon);
        assertEquals(new Position(6, 5), farmer.getPosition());
    }

    @Test
    void testDamagesPlayerOnContact() {
        // Placer farmer rute saa den rammer spilleren
        List<Position> route = Arrays.asList(
            new Position(5, 5),
            new Position(1, 1)  // Spillerens position
        );
        farmer.setRoute(route);

        farmer.onTurn(dungeon);
        // Farmer er nu paa (1,1) = spillerens position
        assertEquals(70, player.getHealth());
    }

    @Test
    void testIsSolid() {
        assertTrue(farmer.isSolid());
    }

    @Test
    void testSymbol() {
        assertEquals('F', farmer.getSymbol());
    }
}
