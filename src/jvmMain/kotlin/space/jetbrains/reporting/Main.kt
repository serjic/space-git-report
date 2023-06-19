package space.jetbrains.reporting

const val totalMonths = 6

class CommittersReportOptions(
    val spaceUrl: String,
    val projectKey: String,
    val repoName: String,
    val documentName: String,
    val folder: String
)

suspend fun main(args: Array<String>) {

    val kind = args[0]

    when (kind) {
        "committers" -> committersReport(args)
        "review-coverage" -> reviewCoverage(args)
        else -> error("Report kind should be one of: committers, review-coverage.")
    }

}