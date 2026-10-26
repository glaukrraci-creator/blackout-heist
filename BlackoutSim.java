import java.util.*;

/**
 * Blackout Heist - simulim i rregullave (1 tick = 1 sekondë loje).
 * v1.1 (PBT 4): rojet me A*, feedback me shkak, gjurma e rrugës, zona e sigurt,
 * tri nivele dhe verifikues BFS që provon se çdo nivel ka zgjidhje.
 */
public class BlackoutSim {
    enum Act { UP, DOWN, LEFT, RIGHT, WAIT }
    enum Status { RUNNING, THIEF_WINS, THIEF_CAUGHT, TIMEOUT }
    enum Defense { GUARD, CAMERA, DOOR }

    // Konstantet (vlerat fillestare)
    static final int ROUND_TIME = 90, BUDGET_START = 6, BUDGET_MAX = 10, BUDGET_EVERY = 8;
    static final int COST_GUARD = 2, COST_CAMERA = 1, COST_DOOR = 2;
    static final int CAMERA_TIME = 10, DOOR_TIME = 8, ALARM_TIME = 15;
    static final int GUARD_RANGE = 3, CAMERA_RANGE = 4, LOW_TIME = 10;

    char[][] map;                 // '#' mur, '.' dysheme, 'D' derë, 'I' send, 'E' dalje
    int w, h;
    int tx, ty;                   // hajduti
    boolean hasItem, itemTaken;
    int timeLeft = ROUND_TIME, budget = BUDGET_START, budgetTimer = 0;
    boolean alarm; int alarmTimer;
    Status status = Status.RUNNING;
    int score;
    final List<int[]> guards = new ArrayList<>();     // x,y,tx,ty (-1 = pa objektiv)
    final List<int[]> cameras = new ArrayList<>();    // x,y,timer
    final Map<String,Integer> doors = new HashMap<>(); // "x,y" -> timer mbyllje

    // Parametrat e nivelit (R15, R16)
    int grace = 0;                                     // sekondat e para pa veprime të mbrojtësit
    EnumSet<Defense> allowed = EnumSet.allOf(Defense.class);

    // Feedback dhe përmbledhja e raundit
    int tickNo = 0;
    String lastMsg = "";                               // kodi i mesazhit të fundit (për testet)
    final List<String> log = new ArrayList<>();        // mesazhet që i shfaqen lojtarit
    final List<int[]> trail = new ArrayList<>();       // gjurma e hajdutit
    final List<Integer> detections = new ArrayList<>(); // tick-u kur ndizet alarmi
    String endReason = "";

    BlackoutSim(String... rows) {
        h = rows.length; w = rows[0].length(); map = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c = rows[y].charAt(x);
            if (c == 'S') { tx = x; ty = y; c = '.'; }
            if (c == 'G') { guards.add(new int[]{x, y, -1, -1}); c = '.'; }
            map[y][x] = c;
        }
        trail.add(new int[]{tx, ty});
    }

    /** Kopje e plotë e gjendjes (përdoret nga verifikuesi i niveleve). */
    BlackoutSim(BlackoutSim o) {
        w = o.w; h = o.h; map = new char[h][];
        for (int y = 0; y < h; y++) map[y] = o.map[y].clone();
        tx = o.tx; ty = o.ty; hasItem = o.hasItem; itemTaken = o.itemTaken;
        timeLeft = o.timeLeft; budget = o.budget; budgetTimer = o.budgetTimer;
        alarm = o.alarm; alarmTimer = o.alarmTimer; status = o.status; score = o.score;
        for (int[] g : o.guards) guards.add(g.clone());
        for (int[] c : o.cameras) cameras.add(c.clone());
        doors.putAll(o.doors);
        grace = o.grace; allowed = EnumSet.copyOf(o.allowed); tickNo = o.tickNo;
        detections.addAll(o.detections);
    }

    void say(String code, String text) { lastMsg = code; log.add("[" + tickNo + " s] " + text); }

    boolean outside(int x, int y) { return x < 0 || y < 0 || x >= w || y >= h; }

    boolean blocked(int x, int y) {
        if (outside(x, y) || map[y][x] == '#') return true;
        return doors.containsKey(x + "," + y);
    }

    boolean los(int x0, int y0, int x1, int y1) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 1; i < n; i++) {
            int x = Math.round(x0 + (x1 - x0) * (float) i / n), y = Math.round(y0 + (y1 - y0) * (float) i / n);
            if (blocked(x, y)) return false;
        }
        return true;
    }

    boolean sees(int x, int y, int range) {
        return Math.abs(x - tx) + Math.abs(y - ty) <= range && los(x, y, tx, ty);
    }

    // ---- A* (R5): hapi i parë i rrugës më të shkurtër, ose null nëse s'ka rrugë ----
    static final int[][] DIRS = {{0,-1},{1,0},{0,1},{-1,0}};   // rend fiks: lart, djathtas, poshtë, majtas

    int[] nextStep(int sx, int sy, int gx, int gy) {
        if (sx == gx && sy == gy) return null;
        int n = w * h, start = sy * w + sx, goal = gy * w + gx;
        int[] g = new int[n], from = new int[n];
        Arrays.fill(g, Integer.MAX_VALUE); Arrays.fill(from, -1);
        boolean[] closed = new boolean[n];
        // {f, h, rendi i futjes, qeliza} -> barazimet zgjidhen gjithmonë njësoj
        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) ->
            a[0] != b[0] ? a[0] - b[0] : a[1] != b[1] ? a[1] - b[1] : a[2] - b[2]);
        int order = 0;
        g[start] = 0;
        open.add(new int[]{Math.abs(gx - sx) + Math.abs(gy - sy), Math.abs(gx - sx) + Math.abs(gy - sy), order++, start});
        while (!open.isEmpty()) {
            int cur = open.poll()[3];
            if (closed[cur]) continue;
            closed[cur] = true;
            if (cur == goal) break;
            int cx = cur % w, cy = cur / w;
            for (int[] d : DIRS) {
                int nx = cx + d[0], ny = cy + d[1];
                if (blocked(nx, ny)) continue;
                int nb = ny * w + nx, ng = g[cur] + 1;
                if (ng < g[nb]) {
                    g[nb] = ng; from[nb] = cur;
                    int hh = Math.abs(gx - nx) + Math.abs(gy - ny);
                    open.add(new int[]{ng + hh, hh, order++, nb});
                }
            }
        }
        if (from[goal] < 0) return null;
        int c = goal;
        while (from[c] != start) c = from[c];
        return new int[]{c % w, c / w};
    }

    // ---- Veprimet e mbrojtësit (R9-R12, R15, R16) ----
    boolean gate(Defense d, int cost) {
        if (status != Status.RUNNING) return reject("ENDED", "Raundi ka mbaruar");
        if (!allowed.contains(d)) return reject("NOT_ALLOWED", "Ky veprim hapet në një nivel më të lartë");
        if (tickNo < grace) return reject("GRACE", "Zona e sigurt: mbrojtësi pret edhe " + (grace - tickNo) + " s");
        if (budget < cost) return reject("BUDGET", "Buxhet i pamjaftueshëm (" + budget + " < " + cost + ")");
        return true;
    }
    boolean sendGuard(int i, int x, int y) {
        if (!gate(Defense.GUARD, COST_GUARD)) return false;
        if (i < 0 || i >= guards.size()) return reject("NO_GUARD", "Nuk ka rojë me numrin " + (i + 1));
        if (blocked(x, y)) return reject("CELL", "Qelizë e pavlefshme për rojën");
        budget -= COST_GUARD; guards.get(i)[2] = x; guards.get(i)[3] = y;
        say("OK_GUARD", "Roja " + (i + 1) + " u nis drejt (" + x + "," + y + ")");
        return true;
    }
    boolean activateCamera(int x, int y) {
        if (!gate(Defense.CAMERA, COST_CAMERA)) return false;
        if (blocked(x, y)) return reject("CELL", "Kamera nuk vendoset në mur");
        budget -= COST_CAMERA; cameras.add(new int[]{x, y, CAMERA_TIME});
        say("OK_CAMERA", "Kamera aktive në (" + x + "," + y + ") për " + CAMERA_TIME + " s");
        return true;
    }
    boolean closeDoor(int x, int y) {
        if (!gate(Defense.DOOR, COST_DOOR)) return false;
        if (outside(x, y) || map[y][x] != 'D') return reject("CELL", "Këtu nuk ka derë");
        if (x == tx && y == ty) return reject("THIEF_ON_DOOR", "Dera nuk mbyllet mbi hajdutin");
        if (doors.containsKey(x + "," + y)) return reject("DOOR_CLOSED", "Dera është tashmë e mbyllur");
        budget -= COST_DOOR; doors.put(x + "," + y, DOOR_TIME);
        say("OK_DOOR", "Dera (" + x + "," + y + ") u mbyll për " + DOOR_TIME + " s");
        return true;
    }
    boolean reject(String code, String text) { say("REJECTED:" + code, "REFUZUAR: " + text); return false; }

    void end(Status s, int sc, String why) {
        status = s; score = sc; endReason = why; say("END:" + s, why + " - pikët " + sc);
    }

    // ---- Një tick: koha -> hajduti -> dalja -> rojet -> kapja -> detektimi -> kohëmatësit ----
    Status tick(Act a) {
        if (status != Status.RUNNING) return status;
        tickNo++;
        timeLeft--;                                                   // R1: t = sekondat pas tick-ut
        int dx = a == Act.LEFT ? -1 : a == Act.RIGHT ? 1 : 0, dy = a == Act.UP ? -1 : a == Act.DOWN ? 1 : 0;
        if (a != Act.WAIT) {                                          // R2
            int nx = tx + dx, ny = ty + dy;
            if (blocked(nx, ny))
                say("BLOCKED", doors.containsKey(nx + "," + ny)
                    ? "Dera e mbyllur: hapet pas " + doors.get(nx + "," + ny) + " s" : "Mur: nuk mund të kalosh");
            else { tx = nx; ty = ny; }
        }
        trail.add(new int[]{tx, ty});
        if (map[ty][tx] == 'I' && !itemTaken) {                      // R3
            itemTaken = true; hasItem = true; map[ty][tx] = '.';
            say("ITEM", "Sendi u mor - shko te dalja E");
        }
        if (map[ty][tx] == 'E') {                                     // R4
            if (hasItem) { end(Status.THIEF_WINS, 100 + 2 * timeLeft + 50, "Hajduti doli me sendin"); return status; }
            say("EXIT_NO_ITEM", "Dalja hapet vetëm me sendin");
        }
        // R5: rojet ndjekin rrugën më të shkurtër (A*)
        for (int i = 0; i < guards.size(); i++) {
            int[] g = guards.get(i);
            int steps = alarm ? 2 : 1;
            for (int s = 0; s < steps; s++) {
                int gx = alarm ? tx : g[2], gy = alarm ? ty : g[3];
                if (gx < 0) break;
                int[] st = nextStep(g[0], g[1], gx, gy);
                if (st == null) {
                    if (!(g[0] == gx && g[1] == gy)) say("GUARD_STUCK", "Roja " + (i + 1) + " pret: rruga e bllokuar");
                    break;
                }
                g[0] = st[0]; g[1] = st[1];
                if (g[0] == tx && g[1] == ty) break;
            }
            if (!alarm && g[2] == g[0] && g[3] == g[1]) { g[2] = g[3] = -1; }
        }
        for (int i = 0; i < guards.size(); i++) {                     // R6
            int[] g = guards.get(i);
            if (g[0] == tx && g[1] == ty) { end(Status.THIEF_CAUGHT, 100 + timeLeft, "Roja " + (i + 1) + " e kapi hajdutin"); return status; }
        }
        // R7, R8: detektimi
        String who = null;
        for (int i = 0; i < guards.size() && who == null; i++)
            if (sees(guards.get(i)[0], guards.get(i)[1], GUARD_RANGE)) who = "roja " + (i + 1);
        for (int[] c : cameras) if (who == null && sees(c[0], c[1], CAMERA_RANGE)) who = "kamera (" + c[0] + "," + c[1] + ")";
        if (who != null) {
            if (!alarm) { detections.add(tickNo); say("DETECTED", "ZBULUAR nga " + who + " - Alarm: rojet shpejtohen"); }
            alarm = true; alarmTimer = ALARM_TIME;
        } else if (alarm && --alarmTimer <= 0) { alarm = false; say("ALARM_OFF", "Alarmi u fik"); }
        // kohëmatësit
        cameras.removeIf(c -> --c[2] <= 0);
        for (Iterator<Map.Entry<String,Integer>> it = doors.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Integer> e = it.next(); e.setValue(e.getValue() - 1);
            if (e.getValue() <= 0) { it.remove(); say("DOOR_OPEN", "Dera (" + e.getKey() + ") u hap"); }
        }
        if (++budgetTimer >= BUDGET_EVERY) { budgetTimer = 0; budget = Math.min(BUDGET_MAX, budget + 1); }   // R13
        if (timeLeft <= 0) end(Status.TIMEOUT, 100, "Koha mbaroi");                                         // R14
        else if (timeLeft == LOW_TIME) say("LOW_TIME", LOW_TIME + " sekonda të mbetura");
        return status;
    }

    /** Ekrani përmbledhës: arsyeja, pikët, momenti i zbulimit dhe gjurma. */
    String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(endReason.isEmpty() ? "Raundi vazhdon" : endReason).append(" | pikët ").append(score)
          .append(" | zbulime në sekondat ").append(detections).append('\n');
        boolean[][] onTrail = new boolean[h][w];
        for (int[] p : trail) onTrail[p[1]][p[0]] = true;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) sb.append(x == tx && y == ty ? 'T' : onTrail[y][x] ? '*' : map[y][x]);
            sb.append('\n');
        }
        return sb.toString();
    }

    // =====================================================================
    // Nivelet (PBT 4)
    // =====================================================================
    static final class Level {
        final String name; final String[] rows; final int budget, grace; final EnumSet<Defense> allowed;
        final String[] script;      // "tick VEPRIMI args": skenari i mbrojtësit referencë
        final String solution;      // zgjidhja e verifikuar: R/L/U/D/W për çdo sekondë
        Level(String name, String[] rows, int budget, int grace, EnumSet<Defense> allowed, String[] script, String solution) {
            this.name = name; this.rows = rows; this.budget = budget; this.grace = grace;
            this.allowed = allowed; this.script = script; this.solution = solution;
        }
        BlackoutSim start() {
            BlackoutSim s = new BlackoutSim(rows);
            s.budget = budget; s.grace = grace; s.allowed = EnumSet.copyOf(allowed);
            return s;
        }
        /** Ekzekuton veprimet e mbrojtësit referencë që i takojnë sekondës aktuale. */
        void defend(BlackoutSim s) {
            for (String line : script) {
                String[] p = line.split(" ");
                if (Integer.parseInt(p[0]) != s.tickNo) continue;
                switch (p[1]) {
                    case "GUARD" -> s.sendGuard(Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                    case "CAMERA" -> s.activateCamera(Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                    case "DOOR" -> s.closeDoor(Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                }
            }
        }
        BlackoutSim play(String moves) {
            BlackoutSim s = start();
            for (char c : moves.toCharArray()) {
                if (s.status != Status.RUNNING) break;
                defend(s); s.tick(act(c));
            }
            return s;
        }
    }

    static Act act(char c) {
        return switch (c) { case 'U' -> Act.UP; case 'D' -> Act.DOWN; case 'L' -> Act.LEFT; case 'R' -> Act.RIGHT; default -> Act.WAIT; };
    }

    static final Level L1 = new Level("Niveli 1 - Rojet (onboarding)", new String[]{
        "############",
        "#S.........#",
        "#.########.#",
        "#...G...I..#",
        "#.########.#",
        "#..........#",
        "#####E######" },
        2, 5, EnumSet.of(Defense.GUARD),
        new String[]{},
        "RRRRRRRRRDDLLRRDDLLLLLD");

    static final Level L2 = new Level("Niveli 2 - Kamerat (core challenge)", new String[]{
        "############",
        "#S...#....E#",
        "#.##.#.###.#",
        "#.#..D.#.GG#",
        "#.#.##.#.###",
        "#......D..I#",
        "############" },
        6, 5, EnumSet.of(Defense.GUARD, Defense.CAMERA),
        new String[]{ "5 CAMERA 6 1", "16 CAMERA 8 5" },
        "WWWWWWWWWWWWWWWWWWWRRRDDRRDDRRRRLLLLUUUURRRR");

    static final Level L3 = new Level("Niveli 3 - Dyert (advanced challenge)", new String[]{
        "################",
        "#S.............#",
        "#.#####D######.#",
        "#.#.........#G.#",
        "#.#.###D###.#..#",
        "#...#.I...D....#",
        "###.#######.####",
        "#........G.....#",
        "#####E##########" },
        6, 5, EnumSet.allOf(Defense.class),
        new String[]{ "5 DOOR 7 2", "10 CAMERA 7 3", "18 DOOR 7 4", "26 GUARD 0 11 5" },
        "WWWWWWWWWWWWWWWWWWRRRRRRDDDDLRUULLLLDDDDRRD");

    static final Level[] LEVELS = { L1, L2, L3 };

    /** Rruga më e shkurtër S -> I -> E pa marrë parasysh rojet (BFS mbi hartë). */
    static String naivePath(Level lv) {
        BlackoutSim s = lv.start();
        int ix = -1, iy = -1, ex = -1, ey = -1;
        for (int y = 0; y < s.h; y++) for (int x = 0; x < s.w; x++) {
            if (s.map[y][x] == 'I') { ix = x; iy = y; }
            if (s.map[y][x] == 'E') { ex = x; ey = y; }
        }
        String a = bfsPath(s, s.tx, s.ty, ix, iy), b = bfsPath(s, ix, iy, ex, ey);
        return a == null || b == null ? null : a + b;
    }

    static String bfsPath(BlackoutSim s, int sx, int sy, int gx, int gy) {
        String[] how = new String[s.w * s.h];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        how[sy * s.w + sx] = ""; q.add(new int[]{sx, sy});
        char[] names = {'U', 'R', 'D', 'L'};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] == gx && c[1] == gy) return how[c[1] * s.w + c[0]];
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DIRS[d][0], ny = c[1] + DIRS[d][1];
                if (s.outside(nx, ny) || s.map[ny][nx] == '#' || how[ny * s.w + nx] != null) continue;
                how[ny * s.w + nx] = how[c[1] * s.w + c[0]] + names[d];
                q.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    /** Verifikuesi: BFS mbi gjendjet e plota të lojës kundër mbrojtësit referencë.
     *  Kthen zgjidhjen më të shpejtë (më pak sekonda), ose null nëse niveli s'ka zgjidhje. */
    static String solve(Level lv) {
        record Node(BlackoutSim s, String moves) {}
        ArrayDeque<Node> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        q.add(new Node(lv.start(), ""));
        char[] acts = {'W', 'R', 'D', 'L', 'U'};
        while (!q.isEmpty()) {
            Node n = q.poll();
            for (char c : acts) {
                BlackoutSim s = new BlackoutSim(n.s);
                lv.defend(s); s.tick(act(c));
                if (s.status == Status.THIEF_WINS) return n.moves + c;
                if (s.status != Status.RUNNING) continue;
                if (seen.add(key(s))) q.add(new Node(s, n.moves + c));
            }
        }
        return null;
    }

    static String key(BlackoutSim s) {
        StringBuilder k = new StringBuilder();
        k.append(s.tickNo).append(':').append(s.tx).append(',').append(s.ty).append(s.hasItem ? 'I' : '-')
         .append(s.alarm ? s.alarmTimer : 0).append(':').append(s.budget);
        for (int[] g : s.guards) k.append('|').append(g[0]).append(',').append(g[1]).append(',').append(g[2]).append(',').append(g[3]);
        for (int[] c : s.cameras) k.append('c').append(c[0]).append(',').append(c[1]).append(',').append(c[2]);
        k.append(new TreeMap<>(s.doors));
        return k.toString();
    }

    // =====================================================================
    // Testet
    // =====================================================================
    static int pass = 0, fail = 0;
    static void check(String id, String what, boolean ok, String got) {
        System.out.printf("%s | %s | %s | %s%n", id, ok ? "PASS" : "FAIL", what, got);
        if (ok) pass++; else fail++;
    }

    static final String[] OPEN = {
        "##########",
        "#S.......#",
        "#........#",
        "#...D....#",
        "#........#",
        "##########" };

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("levels")) { describeLevels(); return; }

        // T01 normal: lëvizje e lirë
        BlackoutSim g = new BlackoutSim(OPEN);
        g.tick(Act.RIGHT);
        check("T01", "Lëvizje normale djathtas", g.tx == 2 && g.ty == 1 && g.timeLeft == 89, "pos=(" + g.tx + "," + g.ty + ") t=" + g.timeLeft);

        // T02 normal: marrja e sendit
        g = new BlackoutSim("#####", "#SI.#", "#####");
        g.tick(Act.RIGHT);
        check("T02", "Marrja e sendit kur hyn në qelizën I", g.hasItem && g.map[1][2] == '.' && g.lastMsg.equals("ITEM"), "hasItem=" + g.hasItem + " msg=" + g.lastMsg);

        // T03 normal: fitore (send + dalje)
        g = new BlackoutSim("######", "#SIE.#", "######");
        g.tick(Act.RIGHT); Status s = g.tick(Act.RIGHT);
        check("T03", "Fitore e hajdutit: send + dalje, pikët 100+2*t+50", s == Status.THIEF_WINS && g.score == 100 + 2 * 88 + 50, "status=" + s + " score=" + g.score);

        // T04 invalid: hyrje në mur
        g = new BlackoutSim(OPEN);
        g.tick(Act.UP);
        check("T04", "Lëvizje në mur refuzohet, hajduti nuk lëviz, koha vazhdon", g.ty == 1 && g.timeLeft == 89 && g.lastMsg.equals("BLOCKED"), "pos=(" + g.tx + "," + g.ty + ") msg=" + g.lastMsg);

        // T05 invalid: veprim pa buxhet të mjaftueshëm
        g = new BlackoutSim(OPEN);
        g.budget = 1; boolean a3 = g.closeDoor(4, 3);
        check("T05", "Veprim me buxhet < kosto refuzohet, buxheti nuk ndryshon, mesazhi tregon shkakun", !a3 && g.budget == 1 && g.doors.isEmpty() && g.lastMsg.equals("REJECTED:BUDGET"), "closeDoor=" + a3 + " budget=" + g.budget + " msg=" + g.lastMsg);

        // T06 invalid: dalja pa send
        g = new BlackoutSim("######", "#SE..#", "######");
        s = g.tick(Act.RIGHT);
        check("T06", "Dalja pa send nuk e fiton raundin dhe lojtari merr mesazh", s == Status.RUNNING && !g.hasItem && g.lastMsg.equals("EXIT_NO_ITEM"), "status=" + s + " msg=" + g.lastMsg);

        // T07 edge: kapje kur roja hyn në qelizën e hajdutit
        g = new BlackoutSim("#######", "#SG...#", "#######");
        g.tick(Act.WAIT);
        g.guards.get(0)[2] = 1; g.guards.get(0)[3] = 1;
        s = g.tick(Act.WAIT);
        check("T07", "Kapje: mbrojtësi merr 100+t dhe HUD tregon të njëjtin t", s == Status.THIEF_CAUGHT && g.score == 100 + 88 && g.timeLeft == 88, "status=" + s + " score=" + g.score + " HUD t=" + g.timeLeft);

        // T08 edge: timeout në sekondën e fundit
        g = new BlackoutSim(OPEN);
        g.timeLeft = 1; s = g.tick(Act.WAIT);
        check("T08", "Timeout: koha = 0 -> fiton mbrojtësi me 100", s == Status.TIMEOUT && g.score == 100, "status=" + s);

        // T09 edge: hajduti del në tick-un e fundit (hajduti lëviz i pari)
        g = new BlackoutSim("######", "#SIE.#", "######");
        g.tick(Act.RIGHT); g.timeLeft = 1; s = g.tick(Act.RIGHT);
        check("T09", "Dalje në sekondën e fundit konsiderohet fitore (hajduti vepron para kohës)", s == Status.THIEF_WINS && g.score == 150, "status=" + s + " score=" + g.score);

        // T10 edge: alarmi dyfishon shpejtësinë e rojës
        g = new BlackoutSim("###########", "#S..G......#", "###########");
        g.alarm = true; g.alarmTimer = 15;
        g.tick(Act.WAIT);
        check("T10", "Gjatë alarmit roja lëviz saktësisht 2 qeliza/tick", g.guards.get(0)[0] == 2 && g.status == Status.RUNNING, "guardX 4->" + g.guards.get(0)[0]);

        // T11 edge: detektim ndizet alarmin
        g = new BlackoutSim("#########", "#S..G....#", "#########");
        g.tick(Act.WAIT);
        check("T11", "Roja brenda rrezes 3 pa pengesë ndez alarmin për 15 s, me mesazh 'ZBULUAR'", g.alarm && g.alarmTimer == 15 && g.lastMsg.equals("DETECTED") && g.detections.equals(List.of(1)), "alarm=" + g.alarm + " log=" + g.log);

        // T12 edge: muri bllokon shikimin
        g = new BlackoutSim("#########", "#S.#G...#", "#########");
        g.tick(Act.WAIT);
        check("T12", "Muri mes hajdutit dhe rojës bllokon detektimin", !g.alarm, "alarm=" + g.alarm);

        // T13 edge: dera e mbyllur bllokon kalimin dhe hapet pas 8 s
        g = new BlackoutSim("#######", "#SD..E#", "#######");
        boolean closed = g.closeDoor(2, 1);
        g.tick(Act.RIGHT);
        boolean stillBlocked = g.tx == 1;
        for (int i = 0; i < 8; i++) g.tick(Act.WAIT);
        g.tick(Act.RIGHT);
        check("T13", "Dera e mbyllur (2 pt) bllokon 8 s, pastaj hapet", closed && stillBlocked && g.tx == 2 && g.budget == 4 + 1, "closed=" + closed + " blocked=" + stillBlocked + " x=" + g.tx + " budget=" + g.budget);

        // T14 edge: dera nuk mbyllet mbi hajdutin
        g = new BlackoutSim("#####", "#SD.#", "#####");
        g.tick(Act.RIGHT);
        boolean r = g.closeDoor(2, 1);
        check("T14", "Nuk mund të mbyllet derë mbi hajdutin; buxheti mbetet 6", !r && g.budget == 6 && g.lastMsg.equals("REJECTED:THIEF_ON_DOOR"), "closeDoor=" + r + " budget=" + g.budget + " msg=" + g.lastMsg);

        // T15 edge: buxheti +1 çdo 8 s dhe kufiri 10
        g = new BlackoutSim(OPEN);
        for (int i = 0; i < 8; i++) g.tick(Act.WAIT);
        int b8 = g.budget;
        for (int i = 0; i < 80; i++) g.tick(Act.WAIT);
        check("T15", "Buxheti rritet +1 çdo 8 s dhe nuk kalon 10", b8 == 7 && g.budget == 10 && g.status == Status.RUNNING, "b@8s=" + b8 + " final=" + g.budget);

        // ---- Testet e reja v1.1 ----
        // T16 A*: roja gjen rrugën rreth murit (problemi 1 i playtest-it)
        g = new BlackoutSim(
            "#######",
            "#.....#",
            "#.###.#",
            "#..G#S#",
            "#######");
        g.sendGuard(0, 3, 1);
        int moves = 0;
        while (g.guards.get(0)[2] >= 0 && moves < 10) { g.tick(Act.WAIT); moves++; }
        check("T16", "A*: roja e dërguar pas murit arrin objektivin me rrugën më të shkurtër", g.guards.get(0)[0] == 3 && g.guards.get(0)[1] == 1 && moves == 6, "guard=(" + g.guards.get(0)[0] + "," + g.guards.get(0)[1] + ") hapa=" + moves);

        // T17 A*: rruga e bllokuar nga dera -> roja pret, pastaj vazhdon
        g = new BlackoutSim("########", "#G.D..S#", "########");
        g.sendGuard(0, 5, 1); g.closeDoor(3, 1);
        g.tick(Act.WAIT); g.tick(Act.WAIT);
        boolean waited = g.guards.get(0)[0] == 1 && g.lastMsg.equals("GUARD_STUCK");
        for (int i = 0; i < 8; i++) g.tick(Act.WAIT);
        check("T17", "Dera e mbyllur ndal rojën me mesazh; pas hapjes roja vazhdon", waited && g.guards.get(0)[0] > 2, "waited=" + waited + " guardX=" + g.guards.get(0)[0]);

        // T18 zona e sigurt: mbrojtësi nuk vepron në 5 s e para
        g = L2.start();
        boolean early = g.activateCamera(9, 3);
        for (int i = 0; i < 5; i++) g.tick(Act.WAIT);
        boolean later = g.activateCamera(9, 3);
        check("T18", "Zona e sigurt: veprimet e mbrojtësit refuzohen në 5 s e para, pastaj lejohen", !early && later, "early=" + early + " later=" + later);

        // T19 veprim i palejuar në nivel
        g = L1.start();
        for (int i = 0; i < 5; i++) g.tick(Act.WAIT);
        boolean cam = g.activateCamera(3, 3);
        check("T19", "Niveli 1: kamera refuzohet me mesazh 'hapet në nivel më të lartë'", !cam && g.lastMsg.equals("REJECTED:NOT_ALLOWED") && g.budget == 2, "camera=" + cam + " msg=" + g.lastMsg);

        // T20 përmbledhja e raundit përmban arsyen dhe gjurmën
        g = new BlackoutSim("#######", "#SG...#", "#######");
        g.tick(Act.WAIT); g.guards.get(0)[2] = 1; g.guards.get(0)[3] = 1; g.tick(Act.WAIT);
        String sum = g.summary();
        check("T20", "Përmbledhja tregon arsyen, pikët dhe momentin e zbulimit", sum.startsWith("Roja 1 e kapi hajdutin | pikët 188 | zbulime në sekondat [1]"), sum.split("\n")[0]);

        // T21-T23 nivelet: zgjidhja e ruajtur fiton; verifikuesi gjen zgjidhje; rruga naive humb
        for (int i = 0; i < LEVELS.length; i++) {
            Level lv = LEVELS[i];
            String found = solve(lv);
            BlackoutSim run = lv.play(lv.solution);
            BlackoutSim naive = lv.play(naivePath(lv) + "W".repeat(90));
            check("T" + (21 + i), lv.name + ": zgjidhja e ruajtur fiton, BFS gjen zgjidhje, rruga më e shkurtër naive humb",
                found != null && run.status == Status.THIEF_WINS && naive.status != Status.THIEF_WINS,
                "zgjidhja=" + run.status + " " + run.score + " (" + lv.solution.length() + " s), BFS=" + (found == null ? "-" : found.length() + " s") + " naive=" + naive.status);
        }

        System.out.printf("%nREZULTATI: %d kaluan, %d dështuan%n", pass, fail);
        if (fail > 0) System.exit(1);
    }

    /** `java BlackoutSim levels` - shtyp hartën, zgjidhjen dhe feedback-un e çdo niveli. */
    static void describeLevels() {
        for (Level lv : LEVELS) {
            String sol = lv.solution != null ? lv.solution : solve(lv);
            String naive = naivePath(lv);
            System.out.println("=== " + lv.name + " ===");
            System.out.println("Zgjidhja (" + (sol == null ? 0 : sol.length()) + " s): " + sol);
            BlackoutSim run = lv.play(sol == null ? "" : sol);
            System.out.print(run.summary());
            run.log.forEach(m -> System.out.println("  " + m));
            System.out.println("Naive (" + naive.length() + " s): " + naive);
            BlackoutSim nr = lv.play(naive + "W".repeat(90));
            System.out.print(nr.summary());
            nr.log.forEach(m -> System.out.println("  " + m));
            System.out.println();
        }
    }
}
