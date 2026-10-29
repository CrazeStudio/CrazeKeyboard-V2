package studio.craze.keyboard.core;

/** Optimal-string-alignment distance (insert, delete, substitute, swap neighbours), bounded for speed. */
public final class EditDistance {
    private EditDistance() {}

    /** Returns the distance, or {@code max + 1} when it is larger than {@code max}. */
    public static int distance(String a, String b, int max) {
        int la = a.length(), lb = b.length();
        if (Math.abs(la - lb) > max) return max + 1;
        if (la == 0) return lb <= max ? lb : max + 1;
        if (lb == 0) return la <= max ? la : max + 1;
        int[] pp = new int[lb + 1];
        int[] p = new int[lb + 1];
        int[] cur = new int[lb + 1];
        for (int j = 0; j <= lb; j++) p[j] = j;
        for (int i = 1; i <= la; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                int v = Math.min(Math.min(cur[j - 1] + 1, p[j] + 1), p[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    v = Math.min(v, pp[j - 2] + 1);
                }
                cur[j] = v;
                if (v < rowMin) rowMin = v;
            }
            if (rowMin > max) return max + 1;
            int[] t = pp; pp = p; p = cur; cur = t;
        }
        return p[lb] <= max ? p[lb] : max + 1;
    }
}
