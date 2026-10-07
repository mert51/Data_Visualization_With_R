library(lattice)
data <<- numeric(100)

function(dataHolder) {
    svg()
    data <<- c(data[2:100], dataHolder$value)

    plot <- xyplot(y ~ x,
        data = data.frame(x = -99:0, y = data),
        main = "Data Visualization with R",
        xlab = "time",
        ylab = "Random(x)",
        type = c("l", "g"),
        col.line = 'dark orange')
    print(plot)
    svg.off()
}