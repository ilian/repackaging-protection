set terminal pdf size 5in, 3in
set output "stats.pdf"
set key autotitle columnhead
set xlabel "Time (s)"
set xrange [0:120]
set ylabel "Unique blocks encountered"
set key outside
set key right top
set offset graph 0, graph 0, graph 0.1, graph 0

plot for [i=0:*] 'stats.dat' index i u ($1/(10**9)):0 w steps title columnheader(1)
# plot 'stats.dat' u ($1/(10**9)):0 w steps
