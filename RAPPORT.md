# Sheep Escape — Projektrapport

## 1. Introduktion

Sheep Escape er et roguelike-spil skrevet i Java, hvor man styrer et får (@) der skal
flygte fra en fold. Man undgår farmere, hunde og fælder, samler items op, og arbejder
sig igennem 4 levels mod friheden.

Spillet køres i terminalen med ANSI-farver og styres med w/a/s/d.

---

## 2. Projektstruktur

```
SheepEscape/
├── src/               ← 23 Java-kildefiler
│   ├── Position.java       Hjælpeklasse: (x,y) koordinat
│   ├── Direction.java       Hjælpeklasse: NORTH/SOUTH/EAST/WEST enum
│   ├── Color.java           Hjælpeklasse: RGB-farver + ANSI-koder
│   ├── Canvas.java          Tegner grid med farver i terminalen
│   ├── Entity.java          ABSTRAKT baseklasse for alt på brættet
│   ├── Player.java          Fåret — health, inventory, bevægelse
│   ├── StoneWall.java       Uødelæggelig mur (#)
│   ├── Fence.java           Hegn der kan sprænges (|)
│   ├── ElectricFence.java   Giver skade, slukker efter 3 hits (~)
│   ├── WaterTrough.java     Healer spilleren (w)
│   ├── Floor.java           Tomt gulv/græs (.)
│   ├── Snare.java           Skjult fælde, afslører sig (. → x)
│   ├── AlarmSensor.java     Line-of-sight fælde (!)
│   ├── Farmer.java          Patruljerer en fast rute (F)
│   ├── Sheepdog.java        Jager spilleren, distraheres af treats (D)
│   ├── HayItem.java         Hø — healer 20 HP (h)
│   ├── BombItem.java        Sprænger hegn efter 3 ture (b)
│   ├── TreatItem.java       Afleder hunde (t)
│   ├── Gate.java            Udgang til næste level (>)
│   ├── Freedom.java         Vind-betingelse, sidste level (*)
│   ├── Dungeon.java         Brættet — holder entities, bevægelse, AI
│   ├── DungeonLoader.java   Loader levels fra .txt filer
│   └── Game.java            Main + spil-loop + UI
├── levels/            ← 4 level-filer
│   ├── level1.txt          Tutorial: hegn + hø + udgang
│   ├── level2.txt          Farmer + snarer
│   ├── level3.txt          Farmer + hund + alarm + el-hegn
│   └── level4.txt          Alt på én gang, frihed som mål
├── tests/             ← 7 JUnit-testfiler (59 tests)
│   ├── PlayerTest.java
│   ├── DungeonTest.java
│   ├── ElectricFenceTest.java
│   ├── SnareTest.java
│   ├── FarmerTest.java
│   ├── SheepdogTest.java
│   └── DungeonLoaderTest.java
├── lib/               ← JUnit jar-fil
└── bin/               ← Kompilerede .class filer
```

---

## 3. OOP-design

### 3.1 Arv (Inheritance)

Alle ting på brættet arver fra den abstrakte klasse `Entity`:

```
Entity (abstrakt)
  ├── Player          — spillerens får
  ├── StoneWall       — uødelæggelig mur
  ├── Fence           — hegn (kan ødelægges)
  ├── ElectricFence   — elektrisk hegn
  ├── WaterTrough     — vandtrug
  ├── Floor           — tomt gulv
  ├── Snare           — skjult fælde
  ├── AlarmSensor     — line-of-sight fælde
  ├── Farmer          — patruljerende fjende
  ├── Sheepdog        — jagende fjende
  ├── HayItem         — hø (heal-item)
  ├── BombItem        — bombe (sprænger hegn)
  ├── TreatItem       — hundegodbid
  ├── Gate            — level-udgang
  └── Freedom         — spilmål
```

Fordelen: Alle entities deler fælles felter (position, symbol, farver, solid)
og metoder (renderOn, onStep, onTurn), men har hver deres specialiserede adfærd.

### 3.2 Polymorfi

Dungeon behandler alle entities ens — den kalder f.eks. `entity.onStep(player, this)`
uden at vide om det er en ElectricFence, HayItem eller Gate. Java vælger automatisk
den rigtige metode baseret på objektets faktiske type (dynamic dispatch).

Eksempel fra `Dungeon.move()`:
```java
List<Entity> atPosition = getEntitiesAt(newPos);
for (Entity e : atPosition) {
    e.onStep(player, this);  // Kalder den RIGTIGE onStep() for hver type
}
```

### 3.3 Encapsulation (indkapsling)

- Alle felter i Entity er `protected` — tilgængelige for subklasser, men ikke udefra
- Position's felter er `private` med getters/setters
- Dungeon's interne tilstand er privat — adgang sker via metoder som
  `getEntityAt()`, `isSolidAt()`, `addMessage()` osv.

### 3.4 Abstraktion

- `Entity` er abstrakt — man kan ikke oprette en `new Entity()`
- `onStep()` og `onTurn()` har default-implementeringer (gør ingenting),
  så simple entities som StoneWall og Floor ikke behøver override dem

---

## 4. Centrale klasser og hvad de gør

### Entity.java — Den abstrakte baseklasse
Alt på brættet arver herfra. Definerer 3 vigtige metoder:
- `renderOn(Canvas)` — tegn entity'en med sit symbol og sine farver
- `onStep(Player, Dungeon)` — hvad sker når spilleren træder på feltet
- `onTurn(Dungeon)` — hvad sker hver tur (fjende-AI, timers)

### Dungeon.java — Spilbrættet
Kernen i spillet. Holder alle entities i en `List<Entity>` og har metoderne:
- `move(Direction)` — flyt spilleren, tjek kollision, kald onStep()
- `show()` — clear canvas, tegn entities, vis canvas
- `tick()` — kald onTurn() på alle entities (enemy AI)

### DungeonLoader.java — Fil-parser
Læser .txt-filer og bygger Dungeon-objekter. Hvert tegn i filen mapper til en Entity.
Farmer-ruter læses fra ROUTE-linjer under kortet.

### Game.java — Spil-loop og UI
Styrer input (Scanner), rendering og spiltilstand.
Adskilt fra Dungeon — Game ved intet om spilmekanik, Dungeon ved intet om UI.

### Canvas.java — Terminal-rendering
Tre parallelle 2D-arrays (content, fgColor, bgColor) tegnes med ANSI escape-koder.
Bruger StringBuilder for at printe hele brættet i ét kald (undgår flimmer).

---

## 5. Interessante designvalg

### Gulv under alt
DungeonLoader tilføjer ALTID et Floor-objekt under alle andre entities.
Når en entity fjernes (f.eks. en bombe der eksploderer), er der stadig grønt gulv.

### Snapshot i tick()
Dungeon.tick() laver en kopi af entity-listen inden den itererer,
fordi onTurn() kan fjerne entities. Uden dette ville vi få
`ConcurrentModificationException`.

### Snare-design
Snaren starter med symbol '.' og grøn farve — visuelt identisk med gulv.
Først når spilleren træder på den, skifter den til 'x' i rødt.
Spilleren kan altså ikke se fælden før det er for sent.

### Treat-distrahering
Sheepdog tjekker FØRST om der er en kastet TreatItem.
Hvis ja, bevæger den sig mod treat i stedet for spilleren.
Når hunden når treat, fjernes den fra brættet.

---

## 6. Test-strategi

### Black-box tests
Vi tester adfærd uden at kende den interne implementering:
- Giver ElectricFence den rigtige mængde skade?
- Slukker den efter præcis 3 hits?
- Kan Snare kun trigger én gang?

### Hvad der testes (59 tests)
| Testklasse            | Antal | Hvad der testes                                    |
|-----------------------|-------|----------------------------------------------------|
| PlayerTest            | 12    | Health, heal, damage, isDead, inventory, bevægelse  |
| DungeonTest           | 14    | Bevægelse, kollision, entity-opslag, spilstatus     |
| ElectricFenceTest     | 6     | Skade, nedtælling, deaktivering efter 3 hits        |
| SnareTest             | 5     | Skjult tilstand, aktivering, éngangsskade           |
| FarmerTest            | 6     | Rute-følgning, cyklisk rute, kontaktskade           |
| SheepdogTest          | 6     | Chase-range, kontaktskade, treat-distrahering       |
| DungeonLoaderTest     | 10    | Fil-loading, entity-placering, dimensioner          |

### Kompilering og kørsel
```bash
cd SheepEscape
javac -encoding UTF-8 -d bin src/*.java
java -cp bin Game

# Tests:
javac -encoding UTF-8 -d bin -cp "lib/junit-platform-console-standalone-1.10.2.jar" src/*.java tests/*.java
java -jar lib/junit-platform-console-standalone-1.10.2.jar -cp bin --scan-classpath
```

---

## 7. Begrænsninger og mangler

- AlarmSensor har altid default retning SOUTH (kan udvides i level-filer)
- Ingen save/load-funktion
- Ingen lyd eller animationer (ren tekst-baseret)
- Sheepdog-AI er simpel "greedy chase" — ingen pathfinding
- Bomber skader kun Fence, ikke andre entity-typer

---

## 8. Genbrugt fra Assignment 4

Tre klasser er genbrugt:
- `Position.java` — næsten uændret (tilføjet distanceTo())
- `Direction.java` — uændret
- `Color.java` — udvidet med nye farvekonstanter

Canvas erstatter BoardDisplay, Entity erstatter BoardElement,
Dungeon erstatter Board, og Game er omskrevet fra bunden.
