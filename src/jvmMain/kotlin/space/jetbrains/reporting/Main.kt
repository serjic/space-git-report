package space.jetbrains.reporting

import kotlinx.datetime.*
import space.jetbrains.api.runtime.BatchInfo
import space.jetbrains.api.runtime.SpaceClient
import space.jetbrains.api.runtime.ktorClientForSpace
import space.jetbrains.api.runtime.resources.projects
import space.jetbrains.api.runtime.types.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

const val totalMonths = 6

class Options(
    val spaceUrl: String,
    val projectKey: String,
    val repoName: String,
    val documentName : String,
    val folder : String
)

suspend fun main(args: Array<String>) {

    val spaceUrl = args[0]
    val projectKey = args[1]
    val repoName = args[2]
    val documentName = args[3]
    val folder = args[4]

    val opts = Options(
        spaceUrl,
        projectKey,
        repoName,
        documentName,
        folder
    )

    val httpClient = ktorClientForSpace()

    val token = System.getenv("SPACE_TOKEN")?.takeIf { it.isNotEmpty() } ?: System.getenv("JB_SPACE_CLIENT_TOKEN") ?: error("No Space Token, set SPACE_TOKEN")

    val client = SpaceClient(
        httpClient,
        opts.spaceUrl,
        token
    )

    val startDate = LocalDate(2023, 1, 1)

    val report = Report(opts)

    (0 until totalMonths).forEach { month ->

        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
        val toDate = startDate.plus(month + 1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

        val allCommits = allCommits(
            client,
            fromDate, toDate,
            opts
        )

        allCommits
            .filter { it.authorProfile != null }
            .groupBy { it.author.name }
            .toList()
            .sortedByDescending { it.second.count() }
            .forEach {
                report.addCommits(
                    it.second.first().authorProfile!!,
                    fromDate,
                    it.second
                )
            }

    }

    client.projects.documents.createDocument(
        project = ProjectIdentifier.Key(opts.projectKey),
        folder = FolderIdentifier.Id(opts.folder),
        name = opts.documentName,
        bodyIn = TextDocumentBodyCreateTypedIn(
            docContent = MdTextDocumentContent(
                markdown = report.md(startDate)
            )
        )
    )

}


class Report(val opts: Options) {
    private val commitInfoByUsername = mutableMapOf<String, ProfileCommitInfo>()

    fun addCommits(profile: TD_MemberProfile, fromDate: LocalDate, commits: List<GitCommitInfo>) {
        commitInfoByUsername.getOrPut(profile.username) {
            ProfileCommitInfo(profile)
        }.apply {
            this.addCommits(fromDate, commits)
        }
    }

    fun csv(startDate: LocalDate) = buildString {
        appendLine("```csv")
        append("Member,")
        appendLine(
            (0 until totalMonths).joinToString(separator = ",") { month ->
                val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                fromDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        )

        commitInfoByUsername
            .toList()
            .sortedByDescending {
                it.second.total
            }
            .forEach {
                append("${opts.spaceUrl}/m/${it.second.profile.username},")
                val profileCommitInfo = it.second
                appendLine(
                    (0 until totalMonths).joinToString(separator = ",") { month ->
                        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                        val count = profileCommitInfo.byDate(fromDate) ?: 0
                        count.toString()
                    }
                )
            }
        appendLine("```")
    }

    fun md(startDate: LocalDate) = buildString {
        appendLine(
            "| Member | " + (0 until totalMonths).joinToString(separator = " | ") { month ->
                val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                fromDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } + " |"
        )
        appendLine(
            "| ------ | " + (0 until totalMonths).joinToString(separator = " | ") {
                "------"
            } + " |"
        )

        commitInfoByUsername
            .toList()
            .sortedByDescending {
                it.second.total
            }
            .forEach {
                append("| ${opts.spaceUrl}/m/${it.second.profile.username} | ")
                val profileCommitInfo = it.second
                appendLine(
                    (0 until totalMonths).joinToString(separator = " | ") { month ->
                        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                        val toDate = startDate.plus(month + 1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
                        val count = profileCommitInfo.byDate(fromDate) ?: 0
                        val commitsLink =
                            "${opts.spaceUrl}/p/${opts.projectKey}/repositories/${opts.repoName}/commits?query=author%3A${profileCommitInfo.profile.username}%20date%3A${
                                format(
                                    fromDate
                                )
                            }..${format(toDate)}%20no-merges%3Atrue&tab=changes"
                        "[${count}]($commitsLink)"
                    } + " |"
                )
            }
    }

    class ProfileCommitInfo(val profile: TD_MemberProfile) {
        private val commitsList = mutableMapOf<LocalDate, Int>()
        fun byDate(fromDate: LocalDate) = commitsList[fromDate]
        val total get() = commitsList.map { it.value }.sum()


        fun addCommits(fromDate: LocalDate, commits: List<GitCommitInfo>) {
            commitsList.getOrPut(fromDate) {
                commits.count()
            }
        }

    }


}

private suspend fun allCommits(
    client: SpaceClient,
    fromDate: LocalDate,
    toDate: LocalDate,
    opts: Options
): MutableList<GitCommitInfo> {
    var batch = BatchInfo(null, 1000)
    val allCommits = mutableListOf<GitCommitInfo>()


    while (true) {

        val res = client.projects.repositories.commits(
            project = ProjectIdentifier.Key(opts.projectKey),
            repository = opts.repoName,
            query = "date:${format(fromDate)}..${format(toDate)} no-merges:true",
            batchInfo = batch
        ) {
            defaultPartial()
            authorProfile {
                defaultPartial()
            }
        }

        allCommits.addAll(res.data)

        if (res.data.count() < 1000)
            break

        batch = BatchInfo(res.next, 1000)

    }
    return allCommits
}

fun format(date: LocalDate): String {
    val pattern = DateTimeFormatter.ofPattern("d-MMM-uuuu")
    return date.toJavaLocalDate().format(pattern)

}
