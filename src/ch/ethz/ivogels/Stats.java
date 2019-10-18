package ch.ethz.ivogels;

public class Stats {
    public static void logConst(Object o) {
        if(o instanceof Integer) {
            System.out.println("STATS_CONST=" + o);
        } else {
            System.err.println("STATS_ERR=Ignoring unknown constant: " + o);
        }
    }
}
