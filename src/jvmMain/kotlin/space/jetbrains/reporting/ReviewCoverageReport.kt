package space.jetbrains.reporting

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

suspend fun reviewCoverage(args: Array<String>) {

}

fun format(date: LocalDate): String {
    val pattern = DateTimeFormatter.ofPattern("d-MMM-uuuu")
    return date.toJavaLocalDate().format(pattern)
}

