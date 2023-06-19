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

    val firstArg = args[0]

    when (firstArg) {
        "committers" -> committersReport(args)
        "review-coverage" -> reviewCoverage(args)
        else -> {
            println("Usage:")
            println("help")
            println("  Show this help")
            println("committers <space_url> <project_key> <repo_name> <document_name> <folder_id>")
            if (firstArg != "help")
                error("Report kind should be one of: committers, review-coverage.")
        }
    }

}