set terminal pdf size 5in, 3in
set output "stats.pdf"

set style boxplot outliers pointtype 7
set style data boxplot
set boxwidth 0.5
set pointsize 0.5

set ylabel "Runtime overhead (ms)"

unset key
set xtics ("miss, no weave" 1, "miss, weave" 2, "hit, no weave" 3, "hit, weave" 4) scale 0.0
set xtics nomirror
set ytics nomirror

plot 'stats.dat' u (1):($1/10**6), '' u (2):($2/10**6), '' u (3):($3/10**6), '' u (4):($4/10**6)
