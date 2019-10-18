set terminal pdf size 5in, 2.5in
set output "const-count.pdf"
unset key
set xlabel "Apps"
set ylabel "Number of unique constant values"
set xtics 5
set logscale y
set style fill transparent solid 0.5 noborder
set style circle radius 0.1
plot 'aggregate-stats.dat' w boxes
