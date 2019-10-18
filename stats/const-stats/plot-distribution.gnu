#set object 1 rectangle from screen 0,0 to screen 1,1 fillcolor rgb"white" behind
set terminal pdf size 5in, 5.5in
set output "const-distribution.pdf"
set multiplot layout 2, 1
unset key
set xlabel "Apps"
set ylabel "Constant values"
set cblabel "Number of occurences per value"
set colorbox
set cbrange [*:*]
set logscale cb
set palette defined (0 'green', 0.5 'red', 1 'blue')
set xtics 5
set yrange [-(2**31):2**31-1]
set style fill transparent solid 0.5 noborder
set style circle radius 0.3
# First plot in linear scale
plot 'stats.dat' \
  using 1:3:2 with circles lc palette

# Second plot in log scale from [1:2^31]
set logscale y 2
set yrange [1:2**31-1]
plot 'stats.dat' \
  using 1:3:2 with circles lc palette
