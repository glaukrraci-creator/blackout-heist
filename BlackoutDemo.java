import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Blackout Heist - Vertical Slice v0.1: moduli INPUT + RENDERING/UI.
 * Rregullat dhe state-i jetojnë vetëm në BlackoutSim (domain); ky skedar vetëm
 * lexon state-in, e vizaton dhe i kthen tastet në veprime (Act).
 * Hajduti luhet me tastierë; mbrojtësi është skenari referencë i nivelit.
 *
 *   javac -encoding UTF-8 BlackoutSim.java Progress.java BlackoutDemo.java
 *   java BlackoutDemo                 -> dritarja e lojës
 *   java BlackoutDemo record <dosja>  -> kornizat PNG të videos demo (pa dritare)
 */
public class BlackoutDemo extends JPanel {
    enum Screen { INTRO, LEVEL_INTRO, PLAY, END }

    static final int W = 960, H = 600, HUD = 58, MSG = 118;
    static final Color BG = new Color(0x0f1218), FLOOR = new Color(0x2b3550), WALL = new Color(0x0b0e15),
        GRID = new Color(0x222a3e), TEXT = new Color(0xe8eaf0), MUTED = new Color(0x8a93a6),
        GOLD = new Color(0xf2b632), EXIT = new Color(0x3fbf7f), THIEF = new Color(0x6fd3ff),
        GUARD = new Color(0xff5a4e), CAMERA = new Color(0x7a8cff), DOOR = new Color(0xb07a45),
        ALARM = new Color(0xff3b30), TRAIL = new Color(0x6fd3ff);

    // Vlerat merren nga konstantet e domain-it: ndryshon rregulli -> ndryshon edhe teksti.
    static final String[] NEW_ELEMENT = {
        "E re: ROJA. Sheh " + BlackoutSim.GUARD_RANGE + " qeliza; muret ia bllokojnë shikimin.",
        "E re: KAMERA. Sheh " + BlackoutSim.CAMERA_RANGE + " qeliza dhe fiket pas " + BlackoutSim.CAMERA_TIME + " sekondash.",
        "E re: DERA. Mbrojtësi e mbyll për " + BlackoutSim.DOOR_TIME + " s; bëhet mur për të gjithë."
    };

    Screen screen = Screen.INTRO;
    int level = 0;
    final Progress progress = recording ? Progress.inMemory() : Progress.load(Progress.defaultFile());
    boolean newRecord;
    BlackoutSim sim;
    final Timer idle = new Timer(1000, e -> step(BlackoutSim.Act.WAIT));

    BlackoutDemo() {
        setPreferredSize(new Dimension(W, H));
        setBackground(BG);
        setFocusable(true);
        idle.setRepeats(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { key(e.getKeyCode()); }
        });
    }

    // ---------------- logjika e ekraneve ----------------
    void startLevel(int i) { level = i; sim = BlackoutSim.LEVELS[i].start(); screen = Screen.LEVEL_INTRO; idle.stop(); }

    void step(BlackoutSim.Act a) {
        if (screen != Screen.PLAY || sim.status != BlackoutSim.Status.RUNNING) return;
        BlackoutSim.LEVELS[level].defend(sim);
        sim.tick(a);
        if (sim.status != BlackoutSim.Status.RUNNING) {
            screen = Screen.END; idle.stop();
            newRecord = sim.status == BlackoutSim.Status.THIEF_WINS && progress.recordWin(level, sim.score);
        }
        repaint();
    }

    void key(int k) {
        switch (screen) {
            case INTRO -> {
                if (k == KeyEvent.VK_ENTER) startLevel(0);
                else if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_3 && k - KeyEvent.VK_1 < progress.unlocked()) startLevel(k - KeyEvent.VK_1);
            }
            case LEVEL_INTRO -> { if (k == KeyEvent.VK_ENTER) { screen = Screen.PLAY; idle.restart(); } }
            case PLAY -> {
                BlackoutSim.Act a = switch (k) {
                    case KeyEvent.VK_W, KeyEvent.VK_UP -> BlackoutSim.Act.UP;
                    case KeyEvent.VK_S, KeyEvent.VK_DOWN -> BlackoutSim.Act.DOWN;
                    case KeyEvent.VK_A, KeyEvent.VK_LEFT -> BlackoutSim.Act.LEFT;
                    case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> BlackoutSim.Act.RIGHT;
                    case KeyEvent.VK_SPACE -> BlackoutSim.Act.WAIT;
                    default -> null;
                };
                if (k == KeyEvent.VK_R) { startLevel(level); screen = Screen.PLAY; idle.restart(); }
                else if (a != null) { idle.restart(); step(a); }
            }
            case END -> {
                boolean won = sim.status == BlackoutSim.Status.THIEF_WINS;
                if (k == KeyEvent.VK_R) startLevel(level);
                else if (k == KeyEvent.VK_N && won && level < 2) startLevel(level + 1);
                else if (k == KeyEvent.VK_ENTER && won && level == 2) screen = Screen.INTRO;
            }
        }
        repaint();
    }

    // ---------------- vizatimi ----------------
    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        render((Graphics2D) g0);
    }

    void render(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG); g.fillRect(0, 0, W, H);
        switch (screen) {
            case INTRO -> drawIntro(g);
            case LEVEL_INTRO -> { drawGame(g, false); drawLevelIntro(g); }
            case PLAY -> drawGame(g, false);
            case END -> { drawGame(g, true); drawEnd(g); }
        }
    }

    // "SansSerif" është font logjik i Java-s: ekziston në Windows, macOS dhe Linux.
    static Font font(int style, int size) { return new Font(Font.SANS_SERIF, style, size); }

    static void center(Graphics2D g, String s, int y, Font f, Color c) {
        g.setFont(f); g.setColor(c);
        g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    void drawIntro(Graphics2D g) {
        // sfond: rrjetë e zbehtë + kon shikimi dekorativ
        g.setColor(GRID);
        for (int x = 0; x < W; x += 40) g.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += 40) g.drawLine(0, y, W, y);
        g.setColor(new Color(255, 214, 102, 28));
        g.fill(new Arc2D.Double(W / 2.0 - 420, -260, 840, 840, 240, 60, Arc2D.PIE));

        center(g, "BLACKOUT HEIST", 150, font(Font.BOLD, 64), TEXT);
        center(g, "Vidh sendin. Dil para se të mbarojë koha.", 192, font(Font.PLAIN, 20), GOLD);

        String[][] rows = {
            {"1", "Merr sendin dhe arri te DALJA brenda 90 sekondave."},
            {"2", "Rojet dhe kamerat të shohin brenda rrezes; muret e bllokojnë shikimin."},
            {"3", "Nëse të zbulojnë, ndizet alarmi dhe rojet vrapojnë dyfish më shpejt."},
            {"4", "Pas çdo raundi rolet ndërrohen: hajdut dhe mbrojtës."},
        };
        int y = 262;
        for (String[] r : rows) {
            g.setFont(font(Font.BOLD, 18)); g.setColor(GOLD); g.drawString(r[0], 214, y);
            g.setFont(font(Font.PLAIN, 17)); g.setColor(TEXT); g.drawString(r[1], 248, y);
            y += 36;
        }
        center(g, "W A S D / shigjetat  lëviz     SPACE  prit     R  rifillo", 450, font(Font.PLAIN, 15), MUTED);
        if ((System.currentTimeMillis() / 600) % 2 == 0 || recording)
            center(g, "Shtyp ENTER për të filluar", 500, font(Font.BOLD, 22), TEXT);
        if (progress.unlocked() > 1)
            center(g, "Nivele të hapura: " + progress.unlocked() + " / 3   (shtyp 1-" + progress.unlocked() + " për të zgjedhur)", 535, font(Font.PLAIN, 14), MUTED);
        center(g, "Glauk Rraci · Yllka Dedinca · Melos Klaiqi", 572, font(Font.PLAIN, 12), MUTED);
    }

    int cell() {
        int w = sim.w, h = sim.h;
        return Math.min(56, Math.min((W - 40) / w, (H - HUD - MSG - 16) / h));
    }
    int ox() { return (W - sim.w * cell()) / 2; }
    int oy() { return HUD + 8 + ((H - HUD - MSG - 16) - sim.h * cell()) / 2; }

    void drawGame(Graphics2D g, boolean showTrail) {
        int c = cell(), ox = ox(), oy = oy();
        // harta
        for (int y = 0; y < sim.h; y++) for (int x = 0; x < sim.w; x++) {
            int px = ox + x * c, py = oy + y * c;
            char t = sim.map[y][x];
            g.setColor(t == '#' ? WALL : FLOOR);
            g.fillRect(px, py, c, c);
            g.setColor(t == '#' ? new Color(0x1a2030) : GRID); g.drawRect(px, py, c, c);
        }
        // zonat e shikimit
        for (int[] cam : sim.cameras) tintVision(g, cam[0], cam[1], BlackoutSim.CAMERA_RANGE, new Color(122, 140, 255, 70));
        for (int[] gd : sim.guards) tintVision(g, gd[0], gd[1], BlackoutSim.GUARD_RANGE,
            sim.alarm ? new Color(255, 59, 48, 70) : new Color(255, 214, 102, 55));
        // dyert, sendi, dalja
        for (int y = 0; y < sim.h; y++) for (int x = 0; x < sim.w; x++) {
            int px = ox + x * c, py = oy + y * c;
            char t = sim.map[y][x];
            if (t == 'D') drawDoor(g, px, py, c, sim.doorTimer(x, y));
            if (t == 'I') {
                g.setColor(GOLD);
                Path2D d = new Path2D.Double();
                d.moveTo(px + c / 2.0, py + c * .2); d.lineTo(px + c * .8, py + c / 2.0);
                d.lineTo(px + c / 2.0, py + c * .8); d.lineTo(px + c * .2, py + c / 2.0); d.closePath();
                g.fill(d);
            }
            if (t == 'E') {
                g.setColor(sim.hasItem ? EXIT : EXIT.darker().darker());
                g.fillRect(px + 3, py + 3, c - 6, c - 6);
                label(g, "DALJA", px, py, c, Color.WHITE, c / 5);
            }
        }
        // gjurma
        if (showTrail || screen == Screen.PLAY) {
            g.setColor(new Color(111, 211, 255, showTrail ? 150 : 60));
            g.setStroke(new BasicStroke(Math.max(2, c / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            List<int[]> tr = sim.trail;
            for (int i = 1; i < tr.size(); i++)
                g.drawLine(ox + tr.get(i - 1)[0] * c + c / 2, oy + tr.get(i - 1)[1] * c + c / 2,
                           ox + tr.get(i)[0] * c + c / 2, oy + tr.get(i)[1] * c + c / 2);
            g.setStroke(new BasicStroke(1));
        }
        // kamerat
        for (int[] cam : sim.cameras) {
            int px = ox + cam[0] * c, py = oy + cam[1] * c;
            g.setColor(CAMERA); g.fillRoundRect(px + c / 5, py + c / 4, c * 3 / 5, c / 2, 8, 8);
            label(g, cam[2] + "s", px, py + c / 3, c, Color.WHITE, c / 4);
        }
        // rojet
        for (int i = 0; i < sim.guards.size(); i++) {
            int[] gd = sim.guards.get(i);
            int px = ox + gd[0] * c, py = oy + gd[1] * c;
            if (gd[2] >= 0) {   // objektivi i rojës
                g.setColor(new Color(255, 90, 78, 160));
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{5, 5}, 0));
                g.drawRect(ox + gd[2] * c + 4, oy + gd[3] * c + 4, c - 8, c - 8);
                g.setStroke(new BasicStroke(1));
            }
            g.setColor(GUARD); g.fillRect(px + c / 6, py + c / 6, c * 2 / 3, c * 2 / 3);
            label(g, "R" + (i + 1), px, py, c, Color.WHITE, c / 3);
        }
        // hajduti
        int tx = ox + sim.tx * c, ty = oy + sim.ty * c;
        g.setColor(THIEF); g.fillOval(tx + c / 6, ty + c / 6, c * 2 / 3, c * 2 / 3);
        label(g, "H", tx, ty, c, BG, c / 3);
        if (sim.hasItem) { g.setColor(GOLD); g.fillOval(tx + c * 2 / 3, ty + 2, c / 4, c / 4); }

        drawHud(g);
        drawMessages(g);
        if (sim.alarm) {   // korniza e alarmit
            g.setColor(new Color(255, 59, 48, 190));
            g.setStroke(new BasicStroke(6)); g.drawRect(3, 3, W - 6, H - 6); g.setStroke(new BasicStroke(1));
        }
    }

    void tintVision(Graphics2D g, int sx, int sy, int range, Color col) {
        int c = cell(), ox = ox(), oy = oy();
        g.setColor(col);
        for (int y = Math.max(0, sy - range); y <= Math.min(sim.h - 1, sy + range); y++)
            for (int x = Math.max(0, sx - range); x <= Math.min(sim.w - 1, sx + range); x++) {
                if (Math.abs(x - sx) + Math.abs(y - sy) > range || sim.map[y][x] == '#') continue;
                if (sim.doorTimer(x, y) != null || !sim.los(sx, sy, x, y)) continue;
                g.fillRect(ox + x * c + 1, oy + y * c + 1, c - 1, c - 1);
            }
    }

    void drawDoor(Graphics2D g, int px, int py, int c, Integer timer) {
        if (timer == null) {
            g.setColor(DOOR); g.setStroke(new BasicStroke(3));
            g.drawRect(px + 4, py + 4, c - 8, c - 8); g.setStroke(new BasicStroke(1));
        } else {
            g.setColor(DOOR); g.fillRect(px + 2, py + 2, c - 4, c - 4);
            g.setColor(BG); g.fillRect(px + c / 2 - 2, py + 6, 4, c - 12);
            label(g, timer + "s", px, py + c / 4, c, Color.WHITE, c / 4);
        }
    }

    static void label(Graphics2D g, String s, int px, int py, int c, Color col, int size) {
        g.setFont(font(Font.BOLD, Math.max(9, size)));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(col);
        g.drawString(s, px + (c - fm.stringWidth(s)) / 2, py + (c + fm.getAscent() - fm.getDescent()) / 2);
    }

    void drawHud(Graphics2D g) {
        g.setColor(new Color(0x151a24)); g.fillRect(0, 0, W, HUD);
        String name = "Niveli " + (level + 1) + ": " + BlackoutSim.LEVELS[level].shortName();
        g.setFont(font(Font.BOLD, 17)); g.setColor(TEXT); g.drawString(name, 20, 36);
        boolean low = sim.timeLeft <= BlackoutSim.LOW_TIME;
        g.setFont(font(Font.BOLD, 26)); g.setColor(low ? ALARM : TEXT);
        String t = sim.timeLeft + " s";
        g.drawString(t, W / 2 - g.getFontMetrics().stringWidth(t) / 2, 39);
        g.setFont(font(Font.PLAIN, 14));
        g.setColor(sim.hasItem ? GOLD : MUTED);
        g.drawString(sim.hasItem ? "Sendi: PO" : "Sendi: JO", W - 380, 35);
        g.setColor(MUTED);
        g.drawString("Mbrojtësi: " + sim.budget + " pikë", W - 270, 35);
        if (sim.alarm) {
            g.setColor(ALARM); g.fillRoundRect(W - 130, 16, 112, 28, 8, 8);
            g.setFont(font(Font.BOLD, 14)); g.setColor(Color.WHITE);
            g.drawString("ALARM " + sim.alarmTimer + " s", W - 120, 35);
        } else if (sim.tickNo < sim.grace) {
            g.setFont(font(Font.PLAIN, 13)); g.setColor(EXIT);
            g.drawString("Zonë e sigurt " + (sim.grace - sim.tickNo) + " s", W - 130, 35);
        }
    }

    void drawMessages(Graphics2D g) {
        int y0 = H - MSG;
        g.setColor(new Color(0x131824)); g.fillRect(0, y0, W, MSG);
        g.setFont(font(Font.BOLD, 12)); g.setColor(MUTED); g.drawString("NGJARJET", 20, y0 + 22);
        List<String> log = sim.log;
        int n = Math.min(4, log.size());
        for (int i = 0; i < n; i++) {
            String m = log.get(log.size() - n + i);
            boolean last = i == n - 1;
            Color col = colorFor(sim.logCodes.get(log.size() - n + i));
            g.setFont(font(last ? Font.BOLD : Font.PLAIN, 14));
            g.setColor(last ? col : col.darker());
            g.drawString(m, 20, y0 + 44 + i * 20);
        }
        if (screen == Screen.PLAY) {
            g.setFont(font(Font.PLAIN, 12)); g.setColor(MUTED);
            String hint = "WASD lëviz · SPACE prit · R rifillo";
            g.drawString(hint, W - 20 - g.getFontMetrics().stringWidth(hint), y0 + 22);
        }
    }

    /** Ngjyra varet nga KODI i mesazhit (kontrata me domain-in), jo nga teksti shqip. */
    static Color colorFor(String code) {
        if (code.equals("DETECTED") || code.equals("END:THIEF_CAUGHT") || code.equals("END:TIMEOUT") || code.equals("LOW_TIME")) return ALARM;
        if (code.startsWith("REJECTED") || code.equals("BLOCKED") || code.equals("EXIT_NO_ITEM") || code.equals("GUARD_STUCK")) return GOLD;
        if (code.equals("ITEM") || code.equals("END:THIEF_WINS")) return EXIT;
        return TEXT;
    }

    void panel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(8, 10, 14, 215)); g.fillRect(0, 0, W, H);
        g.setColor(new Color(0x1b2130)); g.fillRoundRect(x, y, w, h, 16, 16);
        g.setColor(WALL); g.drawRoundRect(x, y, w, h, 16, 16);
    }

    void drawLevelIntro(Graphics2D g) {
        panel(g, 180, 150, 600, 290);
        BlackoutSim.Level lv = BlackoutSim.LEVELS[level];
        center(g, "NIVELI " + (level + 1) + " / 3", 200, font(Font.BOLD, 14), MUTED);
        center(g, lv.name.substring(lv.name.indexOf("- ") + 2), 240, font(Font.BOLD, 26), TEXT);
        center(g, NEW_ELEMENT[level], 290, font(Font.PLAIN, 16), GOLD);
        center(g, "Merr sendin dhe dil te DALJA. Koha: " + BlackoutSim.ROUND_TIME + " s.", 330, font(Font.PLAIN, 15), TEXT);
        center(g, BlackoutSim.LEVELS[level].grace + " sekondat e para mbrojtësi nuk vepron (zonë e sigurt).", 356, font(Font.PLAIN, 14), MUTED);
        center(g, "ENTER  fillo", 410, font(Font.BOLD, 18), TEXT);
    }

    void drawEnd(Graphics2D g) {
        boolean won = sim.status == BlackoutSim.Status.THIEF_WINS;
        int x = 230, y = 170, w = 500, h = 230;
        g.setColor(new Color(8, 10, 14, 120)); g.fillRect(0, 0, W, H);
        g.setColor(new Color(0x1b2130)); g.fillRoundRect(x, y, w, h, 16, 16);
        g.setColor(won ? EXIT : ALARM); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x, y, w, h, 16, 16);
        g.setStroke(new BasicStroke(1));
        center(g, won ? "FITORE" : "U KAPE", y + 50, font(Font.BOLD, 34), won ? EXIT : ALARM);
        center(g, sim.endReason, y + 86, font(Font.PLAIN, 16), TEXT);
        String who = won ? "hajduti" : "mbrojtësi";
        center(g, "Pikët: " + sim.score + " (" + who + ")  ·  Koha e mbetur: " + sim.timeLeft + " s", y + 116, font(Font.PLAIN, 15), TEXT);
        String line = sim.detections.isEmpty() ? "Nuk të zbuluan asnjëherë." : "Të zbuluan në sekondën " + sim.detections.get(0) + ". Shiko gjurmën në hartë.";
        if (won) line += newRecord ? "   Rekord i ri!" : "   Rekordi: " + progress.best(level);
        center(g, line, y + 144, font(Font.PLAIN, 14), MUTED);
        String next = won ? (level < 2 ? "N  niveli tjetër     R  luaj sërish" : "ENTER  kthehu në fillim     R  luaj sërish")
                          : "R  provo sërish";
        center(g, next, y + 194, font(Font.BOLD, 16), TEXT);
    }

    // ---------------- regjistrimi i videos demo ----------------
    static boolean recording = false;

    void frames(File dir, int[] n, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); render(g); g.dispose();
            ImageIO.write(img, "png", new File(dir, String.format("f%05d.png", n[0]++)));
        }
    }

    void playRecorded(File dir, int[] n, String moves, int perTick) throws Exception {
        for (char m : moves.toCharArray()) {
            if (sim.status != BlackoutSim.Status.RUNNING) break;
            step(BlackoutSim.act(m));
            frames(dir, n, perTick);
        }
    }

    static void record(File dir) throws Exception {
        recording = true;
        dir.mkdirs();
        BlackoutDemo d = new BlackoutDemo();
        int[] n = {0};
        final int FPS = 10;
        d.frames(dir, n, 5 * FPS);                                     // ekrani hyrës
        d.startLevel(0); d.frames(dir, n, 3 * FPS);                    // prezantimi i nivelit 1
        d.screen = Screen.PLAY; d.frames(dir, n, FPS);
        d.playRecorded(dir, n, BlackoutSim.naivePath(BlackoutSim.L1), 5);   // provë e gabuar: rruga e shkurtër
        d.frames(dir, n, 4 * FPS);                                     // ekrani "U KAPE" me gjurmën
        d.startLevel(0); d.screen = Screen.PLAY; d.frames(dir, n, FPS);
        d.playRecorded(dir, n, BlackoutSim.L1.solution, 4);            // zgjidhja: rruga e sigurt
        d.frames(dir, n, 4 * FPS);                                     // "FITORE"
        d.startLevel(1); d.frames(dir, n, 3 * FPS);                    // niveli 2: kamerat
        d.screen = Screen.PLAY;
        d.playRecorded(dir, n, BlackoutSim.L2.solution, 3);
        d.frames(dir, n, 3 * FPS);
        d.startLevel(2); d.frames(dir, n, 3 * FPS);                    // niveli 3: dyert
        d.screen = Screen.PLAY;
        d.playRecorded(dir, n, BlackoutSim.L3.solution, 3);
        d.frames(dir, n, 4 * FPS);
        d.screen = Screen.INTRO; d.frames(dir, n, 3 * FPS);
        System.out.println(n[0] + " korniza në " + dir);
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("record")) { record(new File(args[1])); return; }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Blackout Heist - Vertical Slice v0.1");
            BlackoutDemo d = new BlackoutDemo();
            f.add(d); f.pack(); f.setResizable(false);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null); f.setVisible(true);
            d.requestFocusInWindow();
            new Timer(300, e -> { if (d.screen == Screen.INTRO) d.repaint(); }).start();
        });
    }
}
