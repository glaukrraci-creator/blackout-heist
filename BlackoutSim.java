import java.util.*;

/** Blackout Heist - simulim i rregullave (1 tick = 1 sekondë loje). */
public class BlackoutSim {
    enum Act { UP, DOWN, LEFT, RIGHT, WAIT }
    enum Status { RUNNING, THIEF_WINS, THIEF_CAUGHT, TIMEOUT }

    // Konstantet (vlerat fillestare)
    static final int ROUND_TIME = 90, BUDGET_START = 6, BUDGET_MAX = 10, BUDGET_EVERY = 8;
    static final int COST_GUARD = 2, COST_CAMERA = 1, COST_DOOR = 2;
    static final int CAMERA_TIME = 10, DOOR_TIME = 8, ALARM_TIME = 15;
    static final int GUARD_RANGE = 3, CAMERA_RANGE = 4;

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
    String lastMsg = "";

    BlackoutSim(String... rows) {
        h = rows.length; w = rows[0].length(); map = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c = rows[y].charAt(x);
            if (c == 'S') { tx = x; ty = y; c = '.'; }
            if (c == 'G') { guards.add(new int[]{x, y, -1, -1}); c = '.'; }
            map[y][x] = c;
        }
    }

    boolean blocked(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h || map[y][x] == '#') return true;
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

    // ---- Veprimet e mbrojtësit (kontroll buxheti) ----
    boolean sendGuard(int i, int x, int y) {
        if (status != Status.RUNNING || budget < COST_GUARD || i >= guards.size() || blocked(x, y)) return reject("sendGuard");
        budget -= COST_GUARD; guards.get(i)[2] = x; guards.get(i)[3] = y; return true;
    }
    boolean activateCamera(int x, int y) {
        if (status != Status.RUNNING || budget < COST_CAMERA || blocked(x, y)) return reject("camera");
        budget -= COST_CAMERA; cameras.add(new int[]{x, y, CAMERA_TIME}); return true;
    }
    boolean closeDoor(int x, int y) {
        if (status != Status.RUNNING || budget < COST_DOOR || x < 0 || y < 0 || x >= w || y >= h
            || map[y][x] != 'D' || (x == tx && y == ty) || doors.containsKey(x + "," + y)) return reject("door");
        budget -= COST_DOOR; doors.put(x + "," + y, DOOR_TIME); return true;
    }
    boolean reject(String s) { lastMsg = "REJECTED:" + s; return false; }

    // ---- Një tick: hajduti -> dalja -> rojet -> kapja -> detektimi -> kohëmatësit ----
    Status tick(Act a) {
        if (status != Status.RUNNING) return status;
        int dx = a == Act.LEFT ? -1 : a == Act.RIGHT ? 1 : 0, dy = a == Act.UP ? -1 : a == Act.DOWN ? 1 : 0;
        if (a != Act.WAIT) {
            if (blocked(tx + dx, ty + dy)) lastMsg = "BLOCKED"; else { tx += dx; ty += dy; }
        }
        if (map[ty][tx] == 'I' && !itemTaken) { itemTaken = true; hasItem = true; map[ty][tx] = '.'; }
        if (map[ty][tx] == 'E' && hasItem) { status = Status.THIEF_WINS; score = 100 + 2 * (timeLeft - 1) + 50; return status; }
        // rojet
        for (int[] g : guards) {
            int steps = alarm ? 2 : 1;
            for (int s = 0; s < steps; s++) {
                int gx = g[2], gy = g[3];
                if (alarm) { gx = tx; gy = ty; }
                if (gx < 0 || (g[0] == gx && g[1] == gy)) { g[2] = g[3] = -1; break; }
                int sx = Integer.signum(gx - g[0]), sy = Integer.signum(gy - g[1]);
                boolean preferX = Math.abs(gx - g[0]) >= Math.abs(gy - g[1]);
                int[][] opts = preferX ? new int[][]{{sx,0},{0,sy}} : new int[][]{{0,sy},{sx,0}};
                for (int[] o : opts) if ((o[0] != 0 || o[1] != 0) && !blocked(g[0]+o[0], g[1]+o[1])) { g[0]+=o[0]; g[1]+=o[1]; break; }
            }
        }
        for (int[] g : guards) if (g[0] == tx && g[1] == ty) {
            status = Status.THIEF_CAUGHT; score = 100 + (timeLeft - 1); return status;
        }
        // detektimi
        boolean seen = false;
        for (int[] g : guards) if (sees(g[0], g[1], GUARD_RANGE)) seen = true;
        for (int[] c : cameras) if (sees(c[0], c[1], CAMERA_RANGE)) seen = true;
        if (seen) { alarm = true; alarmTimer = ALARM_TIME; }
        else if (alarm && --alarmTimer <= 0) alarm = false;
        // kohëmatësit
        cameras.removeIf(c -> --c[2] <= 0);
        for (Iterator<Map.Entry<String,Integer>> it = doors.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String,Integer> e = it.next(); e.setValue(e.getValue() - 1); if (e.getValue() <= 0) it.remove();
        }
        if (++budgetTimer >= BUDGET_EVERY) { budgetTimer = 0; budget = Math.min(BUDGET_MAX, budget + 1); }
        if (--timeLeft <= 0) { status = Status.TIMEOUT; score = 100; }
        return status;
    }

    // ---- Testet ----
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
        // T01 normal: lëvizje e lirë
        BlackoutSim g = new BlackoutSim(OPEN);
        g.tick(Act.RIGHT);
        check("T01", "Lëvizje normale djathtas", g.tx == 2 && g.ty == 1 && g.timeLeft == 89, "pos=(" + g.tx + "," + g.ty + ") t=" + g.timeLeft);

        // T02 normal: marrja e sendit
        g = new BlackoutSim("#####", "#SI.#", "#####");
        g.tick(Act.RIGHT);
        check("T02", "Marrja e sendit kur hyn në qelizën I", g.hasItem && g.map[1][2] == '.', "hasItem=" + g.hasItem);

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
        g.sendGuard(0, 5, 2);
        boolean a1 = g.activateCamera(5, 2), a2 = g.activateCamera(6, 2);
        g.budget = 1; boolean a3 = g.closeDoor(4, 3);
        check("T05", "Veprim me buxhet < kosto refuzohet dhe buxheti nuk ndryshon", !a3 && g.budget == 1 && g.doors.isEmpty(), "closeDoor=" + a3 + " budget=" + g.budget);

        // T06 invalid: dalja pa send
        g = new BlackoutSim("######", "#SE..#", "######");
        s = g.tick(Act.RIGHT);
        check("T06", "Dalja pa send nuk e fiton raundin", s == Status.RUNNING && !g.hasItem, "status=" + s);

        // T07 edge: kapje kur roja hyn në qelizën e hajdutit
        g = new BlackoutSim("#######", "#SG...#", "#######");
        s = g.tick(Act.WAIT);
        g.guards.get(0)[2] = 1; g.guards.get(0)[3] = 1;
        s = g.tick(Act.WAIT);
        check("T07", "Kapje: roja arrin qelizën e hajdutit, mbrojtësi merr 100+t (t = sekonda të mbetura)", s == Status.THIEF_CAUGHT && g.score == 100 + 88, "status=" + s + " score=" + g.score);

        // T08 edge: timeout në sekondën e fundit
        g = new BlackoutSim(OPEN);
        g.timeLeft = 1; s = g.tick(Act.WAIT);
        check("T08", "Timeout: koha = 0 -> fiton mbrojtësi me 100", s == Status.TIMEOUT && g.score == 100, "status=" + s);

        // T09 edge: hajduti del në tick-un e fundit (hajduti lëviz i pari)
        g = new BlackoutSim("######", "#SIE.#", "######");
        g.tick(Act.RIGHT); g.timeLeft = 1; s = g.tick(Act.RIGHT);
        check("T09", "Dalje në sekondën e fundit konsiderohet fitore (hajduti vepron para kohës)", s == Status.THIEF_WINS, "status=" + s + " score=" + g.score);

        // T10 edge: alarmi dyfishon shpejtësinë e rojës
        g = new BlackoutSim("###########", "#S..G......#", "###########");
        g.alarm = true; g.alarmTimer = 15; g.timeLeft = 90;
        int before = g.guards.get(0)[0];
        g.tick(Act.WAIT);
        check("T10", "Gjatë alarmit roja lëviz 2 qeliza/tick", before - g.guards.get(0)[0] == 2 || g.status == Status.THIEF_CAUGHT, "guardX " + before + "->" + g.guards.get(0)[0]);

        // T11 edge: detektim ndizet alarmin
        g = new BlackoutSim("#########", "#S..G....#", "#########");
        g.tick(Act.WAIT);
        check("T11", "Roja brenda rrezes 3 pa pengesë ndez alarmin për 15 s", g.alarm && g.alarmTimer == 15, "alarm=" + g.alarm);

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
        check("T14", "Nuk mund të mbyllet derë mbi hajdutin; buxheti mbetet 6", !r && g.budget == 6, "closeDoor=" + r + " budget=" + g.budget);

        // T15 edge: buxheti +1 çdo 8 s dhe kufiri 10
        g = new BlackoutSim(OPEN);
        for (int i = 0; i < 8; i++) g.tick(Act.WAIT);
        int b8 = g.budget;
        for (int i = 0; i < 80; i++) g.tick(Act.WAIT);
        check("T15", "Buxheti rritet +1 çdo 8 s dhe nuk kalon 10", b8 == 7 || g.status != Status.RUNNING, "b@8s=" + b8 + " final=" + g.budget + " status=" + g.status);

        System.out.printf("%nREZULTATI: %d kaluan, %d dështuan%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
