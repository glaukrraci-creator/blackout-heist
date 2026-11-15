import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Blackout Heist - moduli PERSISTENCE.
 * Ruan progresin e lojtarit: sa nivele janë hapur dhe rekordin (pikët më të larta) për çdo nivel.
 * Nuk di asgjë për rregullat e lojës apo për UI-në: merr vetëm numra dhe i shkruan në një skedar teksti.
 *
 * Kontrata:
 *   unlocked()                 -> 1..3, sa nivele mund të luhen
 *   best(level)                -> rekordi i nivelit (0 nëse s'është fituar)
 *   recordWin(level, score)    -> hap nivelin tjetër, ruan rekordin; kthen true nëse u thye rekordi
 */
public class Progress {
    static final int LEVEL_COUNT = 3;

    private final Properties p = new Properties();
    private final File file;            // null = vetëm në memorie (p.sh. regjistrimi i videos, testet)

    private Progress(File file) { this.file = file; }

    static File defaultFile() { return new File(System.getProperty("user.home"), ".blackout_heist_progress.properties"); }

    static Progress inMemory() { return new Progress(null); }

    /** Lexon progresin; nëse skedari mungon ose është i dëmtuar, fillon nga zero (loja nuk rrëzohet). */
    static Progress load(File f) {
        Progress pr = new Progress(f);
        if (f != null && f.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                pr.p.load(r);
            } catch (IOException | IllegalArgumentException e) {
                pr.p.clear();
            }
        }
        return pr;
    }

    int unlocked() { return clamp(readInt("unlocked", 1), 1, LEVEL_COUNT); }

    int best(int level) { return Math.max(0, readInt("best." + level, 0)); }

    boolean recordWin(int level, int score) {
        if (level < 0 || level >= LEVEL_COUNT) throw new IllegalArgumentException("Nivel i pavlefshëm: " + level);
        p.setProperty("unlocked", String.valueOf(Math.max(unlocked(), Math.min(LEVEL_COUNT, level + 2))));
        boolean isRecord = score > best(level);
        if (isRecord) p.setProperty("best." + level, String.valueOf(score));
        save();
        return isRecord;
    }

    private void save() {
        if (file == null) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            p.store(w, "Blackout Heist - progresi i lojtarit");
        } catch (IOException e) {
            System.err.println("Progresi nuk u ruajt: " + e.getMessage());   // loja vazhdon edhe pa ruajtje
        }
    }

    private int readInt(String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---- Testet e modulit (pa UI dhe pa rregullat e lojës) ----
    public static void main(String[] args) throws IOException {
        int pass = 0, fail = 0;
        File tmp = File.createTempFile("bh_progress", ".properties");
        tmp.delete();

        Progress a = load(tmp);
        boolean ok = a.unlocked() == 1 && a.best(0) == 0;
        System.out.println("P01 | " + (ok ? "PASS" : "FAIL") + " | Pa skedar: hapet vetëm niveli 1, rekordi 0");
        if (ok) pass++; else fail++;

        boolean rec1 = a.recordWin(0, 284), rec2 = a.recordWin(0, 200);
        Progress b = load(tmp);   // rilexim nga disku
        ok = rec1 && !rec2 && b.unlocked() == 2 && b.best(0) == 284;
        System.out.println("P02 | " + (ok ? "PASS" : "FAIL") + " | Fitorja ruhet në disk: niveli 2 hapet, rekordi 284 nuk zëvendësohet nga 200");
        if (ok) pass++; else fail++;

        try (Writer w = new FileWriter(tmp)) { w.write("unlocked=abc\nbest.0=-5\n"); }
        Progress c = load(tmp);
        ok = c.unlocked() == 1 && c.best(0) == 0;
        System.out.println("P03 | " + (ok ? "PASS" : "FAIL") + " | Skedar i dëmtuar: loja nuk rrëzohet, kthehet në vlerat fillestare");
        if (ok) pass++; else fail++;

        tmp.delete();
        System.out.printf("%nREZULTATI: %d kaluan, %d dështuan%n", pass, fail);
        if (fail > 0) System.exit(1);
    }
}
