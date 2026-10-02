library(lattice)
data <<- numeric(100)

function(dataHolder) {
    svg()
    data <<- c(data[2:100], dataHolder$value)

    plot <- xyplot(y ~ x,
        data = data.frame(x = 0:99, y = data),
        main = "Data Visualization with R",
        xlab = "x (sample index)",
        ylab = "Value",
        type = c("l", "g"),
        col.line = "#5C3317")
    print(plot)
    svg.off()
}